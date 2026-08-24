package commandcenter.command

import com.sun.jna.platform.win32.{Shell32, WinUser}
import com.typesafe.config.Config
import commandcenter.command.CommandError.*
import commandcenter.event.KeyboardShortcut
import commandcenter.tools.Tools
import commandcenter.util.{AppleScript, OS}
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.LowerCamelcase
import fansi.{Color, Str}
import zio.*
import zio.process.Command as PCommand

/** Runs an arbitrary shell command line. Meant as a lightweight way to wire up
  * dozens of small utilities (each its own `commandNames` + `command` in
  * config) without writing a dedicated `Command` for each one.
  *
  * The command line may contain a `{query}` placeholder, which is substituted
  * with whatever text follows the matched alias (or the empty string if nothing
  * follows).
  */
final case class ShellCommand(
    title: String,
    commandTemplate: String,
    outputMode: ShellOutputMode,
    runAsAdmin: Boolean,
    override val commandNames: List[String],
    override val shortcuts: Set[KeyboardShortcut]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.ShellCommand

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      commandLine = commandTemplate.replace("{query}", input.rest.trim)
    } yield PreviewResults.one(
      Preview.unit
        .score(Scores.veryHigh(input.context))
        .onRun(runShell(commandLine))
        .rendered(Renderer.renderDefault(title, Str("Run ") ++ Color.Magenta(commandLine)))
    )

  private def process(commandLine: String): PCommand =
    OS.os match {
      case OS.Windows => PCommand("cmd", "/c", commandLine)
      case _          => PCommand("sh", "-c", commandLine)
    }

  // Windows balloon-tip notifications (see notify.ps1) are backed by the Win32 NOTIFYICONDATA.szInfo field, a
  // fixed WCHAR[256] buffer. Anything past 255 characters gets silently truncated. macOS seems to have no hard limit,
  // but not everything would fix in the viewable area.
  private val notificationMaxLength = 200

  private def notifyOutput(output: String): RIO[Env, Unit] = {
    val trimmed = output.trim
    val message =
      if (trimmed.isEmpty) "(no output)"
      else if (trimmed.length > notificationMaxLength) trimmed.take(notificationMaxLength) + "…"
      else trimmed
    Tools.notify(message, title)
  }

  private def escapeForAppleScriptString(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

  private def openInTerminal(commandLine: String, keepOpen: Boolean): RIO[Env, Unit] =
    OS.os match {
      case OS.Windows =>
        PCommand("cmd", "/c", "start", "cmd", if (keepOpen) "/k" else "/c", commandLine).exitCode.unit

      case OS.MacOS =>
        val toRun = if (runAsAdmin) s"sudo $commandLine" else commandLine
        val finalCommand = if (keepOpen) toRun else s"$toRun; exit"
        val escapedCommand = escapeForAppleScriptString(finalCommand)
        AppleScript.runScript(s"""tell application "Terminal" to do script "$escapedCommand"""").unit

      // Best-effort: unlike Windows/macOS there's no single standard terminal emulator on Linux. This relies on the
      // `x-terminal-emulator` alternative (present on Debian/Ubuntu-derived distros).
      case OS.Linux | OS.Other(_) =>
        val toRun = if (runAsAdmin) s"pkexec sh -c '$commandLine'" else commandLine
        val finalCommand = if (keepOpen) s"$toRun; exec sh" else toRun
        PCommand("x-terminal-emulator", "-e", "sh", "-c", finalCommand).exitCode.unit
    }

  private def handleCapturedOutput(output: String): RIO[Env, Unit] =
    outputMode match {
      case ShellOutputMode.Silent                                   => ZIO.unit
      case ShellOutputMode.Clipboard                                => Tools.setClipboard(output.trim)
      case ShellOutputMode.Notification                             => notifyOutput(output)
      case ShellOutputMode.Window | ShellOutputMode.WindowAutoClose => ZIO.unit // handled separately; unreachable here
    }

  private def shellExecute(operation: String, file: String, parameters: String, showCmd: Int): Task[Unit] =
    ZIO.attempt {
      val result = Shell32.INSTANCE.ShellExecute(null, operation, file, parameters, null, showCmd).intValue()

      // values > 32 indicate success per the Win32 docs
      if (result <= 32)
        throw new RuntimeException(s"ShellExecute failed (error code $result) while trying to run: $file $parameters")
    }

  private def runElevated(commandLine: String): RIO[Env, Unit] =
    (OS.os, outputMode) match {
      case (OS.Windows, ShellOutputMode.Silent) =>
        shellExecute("runas", "cmd.exe", s"/c $commandLine", WinUser.SW_HIDE)

      case (OS.Windows, ShellOutputMode.Window) =>
        shellExecute("runas", "cmd.exe", s"/k $commandLine", WinUser.SW_SHOWNORMAL)

      case (OS.Windows, ShellOutputMode.WindowAutoClose) =>
        shellExecute("runas", "cmd.exe", s"/c $commandLine", WinUser.SW_SHOWNORMAL)

      case (OS.Windows, other) =>
        ZIO.fail(
          new UnsupportedOperationException(
            s"""|outputMode "${other.entryName}" can't be combined with runAsAdmin on Windows -- an elevated
                |process's output can't be captured without extra plumbing. Use "silent" or "window" instead.""".stripMargin
          )
        )

      case (OS.MacOS, ShellOutputMode.Window) =>
        openInTerminal(commandLine, keepOpen = true)

      case (OS.MacOS, ShellOutputMode.WindowAutoClose) =>
        openInTerminal(commandLine, keepOpen = false)

      case (OS.MacOS, _) =>
        val escapedCommand = escapeForAppleScriptString(commandLine)
        PCommand("osascript", "-e", s"""do shell script "$escapedCommand" with administrator privileges""").string
          .flatMap(handleCapturedOutput)

      case (OS.Linux | OS.Other(_), ShellOutputMode.Window) =>
        openInTerminal(commandLine, keepOpen = true)

      case (OS.Linux | OS.Other(_), ShellOutputMode.WindowAutoClose) =>
        openInTerminal(commandLine, keepOpen = false)

      case (OS.Linux | OS.Other(_), _) =>
        PCommand("pkexec", "sh", "-c", commandLine).string.flatMap(handleCapturedOutput)
    }

  private def runNormal(commandLine: String): RIO[Env, Unit] =
    outputMode match {
      case ShellOutputMode.Silent =>
        process(commandLine).successfulExitCode.unit

      case ShellOutputMode.Clipboard =>
        process(commandLine).string.flatMap(output => Tools.setClipboard(output.trim))

      case ShellOutputMode.Notification =>
        process(commandLine).string.flatMap(notifyOutput)

      case ShellOutputMode.Window =>
        openInTerminal(commandLine, keepOpen = true)

      case ShellOutputMode.WindowAutoClose =>
        openInTerminal(commandLine, keepOpen = false)
    }

  private def runShell(commandLine: String): RIO[Env, Unit] =
    if (runAsAdmin) runElevated(commandLine) else runNormal(commandLine)
}

object ShellCommand extends CommandPlugin[ShellCommand] {

  private val unsupportedAdminOutputModesOnWindows: Set[ShellOutputMode] =
    Set(ShellOutputMode.Clipboard, ShellOutputMode.Notification)

  def make(config: Config): IO[CommandPluginError, ShellCommand] =
    for {
      title           <- config.getZIO[Option[String]]("title")
      commandTemplate <- config.getZIO[String]("command")
      outputModeOpt   <- config.getZIO[Option[ShellOutputMode]]("outputMode")
      runAsAdminOpt   <- config.getZIO[Option[Boolean]]("runAsAdmin")
      commandNames    <- config.getZIO[Option[List[String]]]("commandNames")
      shortcuts       <- config.getZIO[Option[Set[KeyboardShortcut]]]("shortcuts")
      outputMode = outputModeOpt.getOrElse(ShellOutputMode.Silent)
      runAsAdmin = runAsAdminOpt.getOrElse(false)
      _ <- ZIO.when(runAsAdmin && OS.os == OS.Windows && unsupportedAdminOutputModesOnWindows.contains(outputMode)) {
             ZIO.fail(
               CommandPluginError.UnexpectedException(
                 new IllegalArgumentException(
                   s"""|ShellCommand "${title.getOrElse(commandTemplate)}": outputMode "${outputMode.entryName}"
                       |can't be combined with runAsAdmin on Windows -- an elevated process's output can't be
                       |captured without extra plumbing. Use outputMode "silent" or "window" instead.""".stripMargin
                 )
               )
             )
           }
    } yield ShellCommand(
      title.getOrElse("Run Command"),
      commandTemplate,
      outputMode,
      runAsAdmin,
      commandNames.getOrElse(Nil),
      shortcuts.getOrElse(Set.empty)
    )
}

sealed trait ShellOutputMode extends EnumEntry with LowerCamelcase

object ShellOutputMode extends Enum[ShellOutputMode] with CirceEnum[ShellOutputMode] {

  /** Fire-and-forget: run the command and don't wait around for output. Good
    * for commands whose purpose is a side effect (opening an app, toggling a
    * setting, restarting a service) rather than producing a value.
    */
  case object Silent extends ShellOutputMode

  /** Capture stdout and copy it to the clipboard once the command finishes. */
  case object Clipboard extends ShellOutputMode

  /** Capture stdout and show it via a native OS notification once the command
    * finishes.
    */
  case object Notification extends ShellOutputMode

  /** Open a new terminal window and run the command in it directly (cmd.exe on
    * Windows, Terminal.app on macOS, `x-terminal-emulator` on Linux), so the OS
    * renders live output itself instead of us capturing and re-rendering it.
    */
  case object Window extends ShellOutputMode

  /** Same as `Window`, except the window closes itself once the command
    * finishes instead of staying open. On macOS this relies on Terminal.app's
    * default "close if the shell exited cleanly" profile setting.
    */
  case object WindowAutoClose extends ShellOutputMode

  lazy val values: IndexedSeq[ShellOutputMode] = findValues
}

package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.util.{ AppleScript, OS }
import commandcenter.view.DefaultView
import zio.blocking.Blocking
import zio.process.{ Command => PCommand, CommandError => PCommandError }
import zio.{ Managed, ZIO }

final case class RebootCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.RebootCommand
  val title: String            = "Reboot"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield PreviewResults.fromIterable(
      Vector(
        Preview.unit
          .onRun(RebootCommand.reboot)
          .score(Scores.high(input.context))
          .view(DefaultView(title, "Restart your computer"))
      ) ++ Vector(
        Preview.unit
          .onRun(RebootCommand.rebootIntoBios)
          .score(Scores.high(input.context))
          .view(DefaultView(s"$title (into BIOS setup)", "Restart your computer and enter BIOS upon startup"))
      ).filter(_ => OS.os == OS.Windows || OS.os == OS.Linux)
    )
}

object RebootCommand extends CommandPlugin[RebootCommand] {
  def make(config: Config): Managed[CommandPluginError, RebootCommand] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
    } yield RebootCommand(commandNames.getOrElse(List("reboot", "restart")))

  def reboot: ZIO[Blocking, Throwable, Unit] =
    ZIO.whenCase(OS.os) {
      case OS.Windows =>
        PCommand("shutdown", "/r", "/t", "0").successfulExitCode.unit

      case OS.Linux =>
        // TODO: Probably need to check for different flavors of Linux. Not sure if this works everywhere.
        PCommand("systemctl", "reboot").successfulExitCode.unit

      case OS.MacOS =>
        AppleScript.runScript("tell application \"System Events\" to restart").unit
    }

  def rebootIntoBios: ZIO[Blocking, PCommandError, Unit] =
    ZIO.whenCase(OS.os) {
      case OS.Windows =>
        PCommand("shutdown", "/r", "/fw", "/t", "0").successfulExitCode.unit

      case OS.Linux =>
        // TODO: Probably need to check for different flavors of Linux. Not sure if this works everywhere.
        PCommand("systemctl", "reboot", "--firmware-setup").successfulExitCode.unit
    }
}

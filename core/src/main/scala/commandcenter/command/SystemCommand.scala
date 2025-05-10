package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Help
import com.monovore.decline.Opts
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.typesafe.config.Config
import commandcenter.command.native.win.PowrProf
import commandcenter.command.SystemCommand.SystemSubcommand
import commandcenter.config.Decoders.*
import commandcenter.util.{AppleScript, OS}
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.*
import zio.process.Command as PCommand

import scala.concurrent.duration.Duration as ScalaDuration

final case class SystemCommand(commandNames: List[String], screensaverDelay: Option[Duration]) extends Command[Unit] {
  val commandType: CommandType = CommandType.SystemCommand
  val title: String = "System Command"

  val sleepCommand = decline.Command("sleep", "Put computer in sleep mode")(Opts(SystemSubcommand.Sleep))
  val monitorOffCommand = decline.Command("monitoroff", "Turn all monitors off")(Opts(SystemSubcommand.MonitorOff))

  val screensaverCommand =
    decline.Command("screensaver", "Activate the screensaver")(Opts(SystemSubcommand.Screensaver))

  val shutdownCommand =
    decline.Command("shutdown", "Shut down the computer")(Opts(SystemSubcommand.Shutdown))

  val helpCommand = decline.Command("help", "Display usage help")(Opts(SystemSubcommand.Help))

  val opts: Opts[SystemSubcommand] =
    Opts.subcommand(sleepCommand) orElse
      Opts.subcommand(monitorOffCommand) orElse
      Opts.subcommand(screensaverCommand) orElse
      Opts.subcommand(shutdownCommand) orElse
      Opts.subcommand(helpCommand) withDefault SystemSubcommand.Help

  val systemCommand: decline.Command[SystemSubcommand] = decline.Command("system", title)(opts)

  override val supportedOS: Set[OS] = Set(OS.Windows, OS.MacOS)

  val SC_MONITORPOWER: Int = 0xf170
  val SC_SCREENSAVE: Int = 0xf140

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed = systemCommand.parse(input.args)
      preview <- ZIO
                   .fromEither(parsed)
                   .fold(
                     h => Preview.help(h).score(Scores.veryHigh(input.context)),
                     subcommand => {
                       val (rendered, run) = subcommand match {
                         case SystemSubcommand.Sleep =>
                           Tuple2(
                             Renderer.renderDefault("Sleep", "Put computer to sleep"),
                             systemSleep
                           )

                         case SystemSubcommand.MonitorOff =>
                           Tuple2(
                             Renderer.renderDefault("Turn off monitor", ""),
                             turnMonitorOff
                           )

                         case SystemSubcommand.Screensaver =>
                           Tuple2(
                             Renderer.renderDefault("Activate screensaver", ""),
                             enableScreenSaver.delay(screensaverDelay.getOrElse(0.seconds))
                           )

                         case SystemSubcommand.Shutdown =>
                           Tuple2(
                             Renderer.renderDefault("Shut down computer", ""),
                             shutdownComputer
                           )

                         case SystemSubcommand.Help =>
                           Tuple2(
                             Renderer.renderDefault(title, HelpMessage.formatted(Help.fromCommand(systemCommand))),
                             ZIO.unit
                           )

                       }

                       Preview.unit.onRun(run).rendered(rendered).score(Scores.veryHigh(input.context))
                     }
                   )
    } yield PreviewResults.one(preview)

  def systemSleep: Task[Unit] =
    OS.os match {
      case OS.Windows =>
        ZIO.attempt {
          PowrProf.SetSuspendState(bHibernate = false, bForce = false, bWakeupEventsDisabled = false)
        }.unit

      case OS.MacOS =>
        AppleScript.runScript("""tell app "System Events" to sleep""").unit

      case _ => ZIO.unit
    }

  def turnMonitorOff: Task[Unit] =
    OS.os match {
      case OS.Windows =>
        ZIO.attempt {
          User32.INSTANCE.SendMessage(
            WinUser.HWND_BROADCAST,
            WinUser.WM_SYSCOMMAND,
            new WPARAM(SC_MONITORPOWER),
            new LPARAM(2)
          )
        }.unit

      case OS.MacOS =>
        AppleScript.runScript("""activate application "ScreenSaverEngine"""").unit

      case _ => ZIO.unit
    }

  def enableScreenSaver: Task[Unit] =
    OS.os match {
      case OS.Windows =>
        ZIO.attempt {
          User32.INSTANCE.SendMessage(
            WinUser.HWND_BROADCAST,
            WinUser.WM_SYSCOMMAND,
            new WPARAM(SC_SCREENSAVE),
            new LPARAM(0)
          )
        }.unit

      case OS.MacOS =>
        AppleScript.runScript("""activate application "ScreenSaverEngine"""").unit

      case _ => ZIO.unit
    }

  def shutdownComputer: Task[Unit] =
    OS.os match {
      case OS.Windows =>
        PCommand("shutdown", "/s", "/t", "0").successfulExitCode.unit

      case OS.MacOS =>
        AppleScript.runScript("""tell app "System Events" to shut down"""").unit

      case _ => ZIO.unit
    }
}

object SystemCommand extends CommandPlugin[SystemCommand] {

  def make(config: Config): IO[CommandPluginError, SystemCommand] =
    for {
      commandNames     <- config.getZIO[Option[List[String]]]("commandNames")
      screensaverDelay <- config.getZIO[Option[ScalaDuration]]("screensaverDelay")
    } yield SystemCommand(commandNames.getOrElse(List("system")), screensaverDelay.map(Duration.fromScala))

  sealed trait SystemSubcommand

  object SystemSubcommand {
    case object Sleep extends SystemSubcommand
    case object MonitorOff extends SystemSubcommand
    case object Screensaver extends SystemSubcommand
    case object Shutdown extends SystemSubcommand
    case object Help extends SystemSubcommand
  }
}

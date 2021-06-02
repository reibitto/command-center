package commandcenter.command

import com.monovore.decline
import com.monovore.decline.{ Help, Opts }
import com.sun.jna.platform.win32.WinDef.{ LPARAM, WPARAM }
import com.sun.jna.platform.win32.{ User32, WinUser }
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.SystemCommand.SystemSubcommand
import commandcenter.command.native.win.PowrProf
import commandcenter.util.OS
import commandcenter.view.DefaultView
import zio.{ Task, TaskManaged, ZIO, ZManaged }

final case class SystemCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.SystemCommand
  val title: String            = "System Command"

  val sleepCommand       = decline.Command("sleep", "Put computer in sleep mode")(Opts(SystemSubcommand.Sleep))
  val monitorOffCommand  = decline.Command("monitoroff", "Turn all monitors off")(Opts(SystemSubcommand.MonitorOff))
  val screensaverCommand =
    decline.Command("screensaver", "Activate the screensaver")(Opts(SystemSubcommand.Screensaver))
  val helpCommand        = decline.Command("help", "Display usage help")(Opts(SystemSubcommand.Help))

  val opts: Opts[SystemSubcommand] =
    Opts.subcommand(sleepCommand) orElse
      Opts.subcommand(monitorOffCommand) orElse
      Opts.subcommand(screensaverCommand) orElse
      Opts.subcommand(helpCommand) withDefault SystemSubcommand.Help

  val systemCommand: decline.Command[SystemSubcommand] = decline.Command("system", title)(opts)

  override val supportedOS: Set[OS] = Set(OS.Windows)

  val SC_MONITORPOWER: Int = 0xf170
  val SC_SCREENSAVE: Int   = 0xf140

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input   <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed   = systemCommand.parse(input.args)
      preview <- ZIO
                   .fromEither(parsed)
                   .fold(
                     h => Preview.help(h).score(Scores.high(input.context)),
                     subcommand => {
                       val (view, run) = subcommand match {
                         case SystemSubcommand.Sleep =>
                           Tuple2(
                             DefaultView("Sleep", "Put computer to sleep"),
                             Task {
                               PowrProf
                                 .SetSuspendState(bHibernate = false, bForce = false, bWakeupEventsDisabled = false)
                               ()
                             }
                           )

                         case SystemSubcommand.MonitorOff =>
                           Tuple2(
                             DefaultView("Turn off monitor", ""),
                             Task {
                               User32.INSTANCE.SendMessage(
                                 WinUser.HWND_BROADCAST,
                                 WinUser.WM_SYSCOMMAND,
                                 new WPARAM(SC_MONITORPOWER),
                                 new LPARAM(2)
                               )
                               ()
                             }
                           )

                         case SystemSubcommand.Screensaver =>
                           Tuple2(
                             DefaultView("Activate screensaver", ""),
                             Task {
                               User32.INSTANCE.SendMessage(
                                 WinUser.HWND_BROADCAST,
                                 WinUser.WM_SYSCOMMAND,
                                 new WPARAM(SC_SCREENSAVE),
                                 new LPARAM(0)
                               )
                               ()
                             }
                           )

                         case SystemSubcommand.Help =>
                           Tuple2(
                             DefaultView(title, HelpMessage.formatted(Help.fromCommand(systemCommand))),
                             ZIO.unit
                           )

                       }

                       Preview.unit.onRun(run).view(view).score(Scores.high(input.context))
                     }
                   )
    } yield PreviewResults.one(preview)
}

object SystemCommand extends CommandPlugin[SystemCommand] {
  def make(config: Config): TaskManaged[SystemCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield SystemCommand(commandNames.getOrElse(List("system")))
    )

  sealed trait SystemSubcommand

  object SystemSubcommand {
    case object Sleep       extends SystemSubcommand
    case object MonitorOff  extends SystemSubcommand
    case object Screensaver extends SystemSubcommand
    case object Help        extends SystemSubcommand
  }
}

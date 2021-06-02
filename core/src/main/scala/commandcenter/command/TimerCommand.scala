package commandcenter.command

import cats.syntax.apply._
import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommonArgs._
import commandcenter.util.{ AppleScript, OS, PowerShellScript }
import commandcenter.view.DefaultView
import zio.duration._
import zio.{ TaskManaged, ZIO, ZManaged }

final case class TimerCommand(commandNames: List[String]) extends Command[Unit] {
  // TODO: Add option to list active timers and also one for canceling them

  val commandType: CommandType = CommandType.OpacityCommand
  val title: String            = "Timer"

  val messageOpt  = Opts.option[String]("message", "Message to display when timer is done", "m").orNone
  val durationArg = Opts.argument[Duration]("duration")

  val timerCommand = decline.Command("timer", title)((durationArg, messageOpt).tupled)

  val notifyFn =
    if (OS.os == OS.MacOS)
      AppleScript.loadFunction2[String, String]("applescript/system/notify.applescript")
    else
      PowerShellScript.loadFunction2[String, String]("powershell/system/notify.ps1")

  // TODO: Support all OSes
  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input   <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed   = timerCommand.parse(input.args)
      message <- ZIO
                   .fromEither(parsed)
                   .fold(HelpMessage.formatted, f => fansi.Str(s"Reminder after ${f._1.render}"))
    } yield {
      val run = for {
        (delay, timerMessageOpt) <- ZIO.fromEither(parsed).mapError(RunError.CliError)
        timerDoneMessage          = timerMessageOpt.getOrElse(s"Timer completed after ${delay.render}")
        // Note: Can't use JOptionPane here for message dialogs until Graal native-image supports Swing
        _                        <- notifyFn(timerDoneMessage, "Command Center Timer Event").delay(delay)
      } yield ()

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.high(input.context))
          .view(DefaultView(title, message))
      )
    }

}

object TimerCommand extends CommandPlugin[TimerCommand] {
  def make(config: Config): TaskManaged[TimerCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield TimerCommand(commandNames.getOrElse(List("timer", "remind")))
    )
}

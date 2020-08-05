package commandcenter.command

import cats.syntax.apply._
import com.monovore.decline
import com.monovore.decline.Opts
import commandcenter.CCRuntime.Env
import commandcenter.command.CommonArgs._
import commandcenter.util.{ AppleScript, OS }
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO
import zio.duration._

final case class TimerCommand() extends Command[Unit] {
  // TODO: Add option to list active timers and also one for canceling them

  val commandType: CommandType = CommandType.OpacityCommand

  val commandNames: List[String] = List("timer", "remind")

  val title: String = "Timer"

  val messageOpt  = Opts.option[String]("message", "Message to display when timer is done", "m").orNone
  val durationArg = Opts.argument[Duration]("duration")

  val timerCommand = decline.Command("timer", title)((durationArg, messageOpt).tupled)

  val notifyFn = AppleScript.loadFunction2[String, String]("applescript/system/notify.applescript")

  // TODO: Support all OSes
  override val supportedOS: Set[OS] = Set(OS.MacOS)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input   <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed   = timerCommand.parse(input.args)
      message <- ZIO
                   .fromEither(parsed)
                   .fold(HelpMessage.formatted, f => fansi.Str(s"Reminder after ${f._1.render}"))
    } yield {
      val run = for {
        (delay, timerMessageOpt) <- ZIO.fromEither(parsed)
        timerDoneMessage          = timerMessageOpt.getOrElse(s"Timer completed after ${delay.render}")
        // Note: Can't use JOptionPane here for message dialogs until Graal native-image supports Swing
        _                        <- notifyFn(timerDoneMessage, "Command Center Timer Event").delay(delay)
      } yield ()

      List(
        Preview.unit
          .onRun(run.ignore)
          .score(Scores.high(input.context))
          .view(DefaultView(title, message))
      )
    }

}

object TimerCommand extends CommandPlugin[TimerCommand] {
  implicit val decoder: Decoder[TimerCommand] = Decoder.const(TimerCommand())
}

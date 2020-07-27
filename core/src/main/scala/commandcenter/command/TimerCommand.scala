package commandcenter.command

import cats.syntax.apply._
import com.monovore.decline
import com.monovore.decline.Opts
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.command.CommonArgs._
import commandcenter.util.{ AppleScript, OS }
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.duration.Duration
import zio.{ UIO, ZIO }

final case class TimerCommand() extends Command[Unit] {
  // TODO: Add option to list active timers and also one for canceling them

  val commandType: CommandType = CommandType.OpacityCommand

  val commandNames: List[String] = List("timer", "remind")

  val title: String = "Timer"

  val messageOpt  = Opts.option[String]("message", "Message to display when timer is done", "m").orNone
  val durationArg = Opts.argument[Duration]("duration")

  val timerCommand = decline.Command("opacity", title)((durationArg, messageOpt).tupled)

  val notifyFn = AppleScript.loadFunction2[String, String]("applescript/system/notify.applescript")

  // TODO: Support all OSes
  override val supportedOS: Set[OS] = Set(OS.MacOS)

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): ZIO[Env, CommandError, List[PreviewResult[Unit]]] = {
    val parsed = timerCommand.parse(args)

    for {
      message <- ZIO
                  .fromEither(parsed)
                  .foldM(
                    help => UIO(HelpMessage.formatted(help)),
                    f => UIO(fansi.Str(s"Reminder after ${f._1.render}"))
                  )
    } yield {
      val run = for {
        (delay, timerMessageOpt) <- ZIO.fromEither(parsed)
        timerDoneMessage         = timerMessageOpt.getOrElse(s"Timer completed after ${delay.render}")
        // Note: Can't use JOptionPane here for message dialogs until Graal native-image supports Swing
        _ <- notifyFn(timerDoneMessage, "Command Center Timer Event").delay(delay)
      } yield ()

      List(
        Preview.unit
          .onRun(run.ignore)
          .score(Scores.high(context))
          .view(DefaultView(title, message))
      )
    }
  }

}

object TimerCommand extends CommandPlugin[TimerCommand] {
  implicit val decoder: Decoder[TimerCommand] = Decoder.const(TimerCommand())
}

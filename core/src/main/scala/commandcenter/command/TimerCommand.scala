package commandcenter.command

import cats.syntax.apply.*
import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.command.CommonArgs.*
import commandcenter.command.TimerCommand.ActiveTimer
import commandcenter.tools.Tools
import commandcenter.util.OS
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.Color
import fansi.Str
import zio.*

import java.time.Instant

final case class TimerCommand(
    commandNames: List[String],
    activeTimersRef: Ref[Set[ActiveTimer]]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.TimerCommand
  val title: String = "Timer"

  val messageOpt = Opts.option[String]("message", "Message to display when timer is done", "m").orNone
  val durationArg = Opts.argument[Duration]("duration")

  val timerCommand = decline.Command("timer", title)((durationArg, messageOpt).tupled)

  // TODO: Support all OSes
  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed = timerCommand.parse(input.args)
      message <- ZIO
                   .fromEither(parsed)
                   .foldZIO(
                     h =>
                       for {
                         activeTimers <- activeTimersRef.get
                         now          <- Clock.instant
                         lines =
                           activeTimers.map { timer =>
                             val relativeTime = java.time.Duration.between(now, timer.runsAt)
                             s"\n${Color.Green(Str(timer.message.getOrElse("untitled")))} in ${relativeTime.render}"
                           }
                       } yield HelpMessage.formatted(h) ++ lines.mkString,
                     { case (duration, messageOpt) =>
                       val messagePart = messageOpt.fold("")(m => s" for ${Color.Magenta(m)}")

                       ZIO.succeed(Str(s"Reminder after ${duration.render}$messagePart"))
                     }
                   )
    } yield {
      val run = for {
        (delay, timerMessageOpt) <- ZIO.fromEither(parsed).orElseFail(RunError.Ignore)
        runsAt                   <- Clock.instant.map(_.plus(delay))
        activeTimer = ActiveTimer(timerMessageOpt, runsAt)
        _ <- activeTimersRef.update(_ + activeTimer)
        timerDoneMessage = timerMessageOpt.getOrElse(s"Timer completed after ${delay.render}")
        // Note: Can't use JOptionPane here for message dialogs until Graal native-image supports Swing
        _ <- Tools.notify(timerDoneMessage, "Command Center Timer Event").delay(delay)
        _ <- activeTimersRef.update(_ - activeTimer)
      } yield ()

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.veryHigh(input.context))
          .rendered(Renderer.renderDefault(title, message))
      )
    }

}

object TimerCommand extends CommandPlugin[TimerCommand] {

  final case class ActiveTimer(message: Option[String], runsAt: Instant)

  def make(config: Config): IO[CommandPluginError, TimerCommand] =
    for {
      activeTimersRef <- Ref.make(Set.empty[ActiveTimer])
      commandNames    <- config.getZIO[Option[List[String]]]("commandNames")
    } yield TimerCommand(commandNames.getOrElse(List("timer", "remind")), activeTimersRef)

}

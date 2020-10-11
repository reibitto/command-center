package commandcenter.command

import java.time.{ Instant, ZoneId }
import java.time.format.{ DateTimeFormatter, FormatStyle }
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools
import zio.{ clock, Task, TaskManaged, ZIO, ZManaged }

final case class EpochUnixCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.EpochUnixCommand
  val title: String            = "Epoch (Unix time)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input           <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      (output, score) <- if (input.rest.trim.isEmpty) {
                           (clock.currentTime(TimeUnit.SECONDS).map(time => (time.toString, Scores.high)))
                         } else {
                           input.rest.toLongOption match {
                             case Some(seconds) =>
                               Task {
                                 val formatted = Instant
                                   .ofEpochSecond(seconds)
                                   .atZone(ZoneId.systemDefault())
                                   .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

                                 val score = if (seconds < 100000000000L) {
                                   Scores.high(input.context)
                                 } else {
                                   Scores.high(input.context) * 0.9
                                 }

                                 (formatted, score)
                               }.mapError(CommandError.UnexpectedException)

                             case None => ZIO.fail(CommandError.NotApplicable)
                           }
                         }
    } yield List(
      Preview(output)
        .score(score)
        .onRun(tools.setClipboard(output))
    )
}

object EpochUnixCommand extends CommandPlugin[EpochUnixCommand] {
  def make(config: Config): TaskManaged[EpochUnixCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield EpochUnixCommand(commandNames.getOrElse(List("epochunix")))
    )
}

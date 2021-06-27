package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools.Tools
import zio._

import java.time.format.{ DateTimeFormatter, FormatStyle }
import java.time.{ Instant, ZoneId }
import java.util.concurrent.TimeUnit

final case class EpochMillisCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.EpochMillisCommand
  val title: String            = "Epoch (milliseconds)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input           <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      (output, score) <- if (input.rest.trim.isEmpty) {
                           clock.currentTime(TimeUnit.MILLISECONDS).map(time => (time.toString, Scores.high))
                         } else {
                           input.rest.toLongOption match {
                             case Some(millis) =>
                               Task {
                                 val formatted = Instant
                                   .ofEpochMilli(millis)
                                   .atZone(ZoneId.systemDefault())
                                   .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

                                 val score = if (millis < 100000000000L) {
                                   Scores.high(input.context) * 0.9
                                 } else {
                                   Scores.high(input.context)
                                 }

                                 (formatted, score)
                               }.mapError(CommandError.UnexpectedException)

                             case None => ZIO.fail(CommandError.NotApplicable)
                           }
                         }
    } yield PreviewResults.one(
      Preview(output)
        .score(score)
        .onRun(Tools.setClipboard(output))
    )
}

object EpochMillisCommand extends CommandPlugin[EpochMillisCommand] {
  def make(config: Config): Managed[CommandPluginError, EpochMillisCommand] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
    } yield EpochMillisCommand(commandNames.getOrElse(List("epochmillis")))
}

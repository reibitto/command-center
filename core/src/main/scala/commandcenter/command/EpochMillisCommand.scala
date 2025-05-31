package commandcenter.command

import com.typesafe.config.Config
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.*

import java.time.{Instant, ZoneId}
import java.time.format.{DateTimeFormatter, FormatStyle}
import java.util.concurrent.TimeUnit

final case class EpochMillisCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.EpochMillisCommand
  val title: String = "Epoch (milliseconds)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input           <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      (output, score) <- if (input.rest.trim.isEmpty) {
                           Clock.currentTime(TimeUnit.MILLISECONDS).map(time => (time.toString, Scores.veryHigh))
                         } else {
                           input.rest.toLongOption match {
                             case Some(millis) =>
                               ZIO.attempt {
                                 val formatted = Instant
                                   .ofEpochMilli(millis)
                                   .atZone(ZoneId.systemDefault())
                                   .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

                                 val score = if (millis < 100000000000L) {
                                   Scores.veryHigh(input.context) * 0.9
                                 } else {
                                   Scores.veryHigh(input.context)
                                 }

                                 (formatted, score)
                               }.mapError(CommandError.UnexpectedError(this))

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

  def make(config: Config): IO[CommandPluginError, EpochMillisCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield EpochMillisCommand(commandNames.getOrElse(List("epochmillis")))
}

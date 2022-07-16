package commandcenter.command

import com.typesafe.config.Config
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.{clock, Managed, Task, ZIO}

import java.time.{Instant, ZoneId}
import java.time.format.{DateTimeFormatter, FormatStyle}
import java.util.concurrent.TimeUnit

final case class EpochUnixCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.EpochUnixCommand
  val title: String = "Epoch (Unix time)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      (output, score) <- if (input.rest.trim.isEmpty) {
                           clock.currentTime(TimeUnit.SECONDS).map(time => (time.toString, Scores.high))
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
    } yield PreviewResults.one(
      Preview(output)
        .score(score)
        .onRun(Tools.setClipboard(output))
    )
}

object EpochUnixCommand extends CommandPlugin[EpochUnixCommand] {

  def make(config: Config): Managed[CommandPluginError, EpochUnixCommand] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
    } yield EpochUnixCommand(commandNames.getOrElse(List("epochunix")))
}

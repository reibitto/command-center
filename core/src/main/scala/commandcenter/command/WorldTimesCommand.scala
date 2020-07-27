package commandcenter.command

import java.time.format.DateTimeFormatter
import java.time.{ OffsetDateTime, ZoneId, ZonedDateTime }

import commandcenter.{ CommandContext, TerminalType }
import commandcenter.view.View
import io.circe.Decoder
import zio.{ IO, UIO }

final case class WorldTimesCommand(dateTimeFormat: String, zones: List[TimeZone]) extends Command[Unit] {
  val commandType: CommandType = CommandType.WorldTimesCommand

  val commandNames: List[String] = List("time", "times")

  val title: String = "World Times"

  override def keywordPreview(keyword: String, context: CommandContext): IO[CommandError, List[PreviewResult[Unit]]] = {
    val now = OffsetDateTime.now

    val times = zones.map(tz => WorldTimesResult(tz.zoneId, tz.displayName, now.atZoneSameInstant(tz.zoneId)))

    UIO(List(Preview.unit.view(WorldTimesResults(dateTimeFormat, times, context)).score(Scores.high(context))))
  }
}

object WorldTimesCommand extends CommandPlugin[WorldTimesCommand] {
  implicit val decoder: Decoder[WorldTimesCommand] =
    Decoder.forProduct2("dateTimeFormat", "zones")(WorldTimesCommand.apply)
}

final case class WorldTimesResults(dateTimeFormat: String, results: List[WorldTimesResult], context: CommandContext) {
  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormat)
}

object WorldTimesResults {
  implicit val displayable: View[WorldTimesResults] = View.ansi { results =>
    // TODO: This is a temporary hack. Aligning should be handled automatically.
    val align = if (results.context.terminal.terminalType == TerminalType.Swing) "  " else ""

    intersperse(fansi.Str(s"\n$align")) {
      results.results
        .map(r => fansi.Color.Cyan(r.displayName) ++ fansi.Str(" ") ++ fansi.Str(results.formatter.format(r.dateTime)))
    }.foldLeft(fansi.Str(""))(_ ++ _)
  }

  // TODO: Move to extension object
  def intersperse[E](separator: E)(xs: List[E]): List[E] = (separator, xs) match {
    case (_, Nil)             => Nil
    case (_, list @ _ :: Nil) => list
    case (sep, y :: ys)       => y :: sep :: intersperse(sep)(ys)
  }
}

final case class WorldTimesResult(zone: ZoneId, displayName: String, dateTime: ZonedDateTime)

final case class TimeZone(zoneId: ZoneId, name: Option[String]) {
  def displayName: String = name.getOrElse(zoneId.getId)
}

object TimeZone {
  implicit val decoder: Decoder[TimeZone] = Decoder.forProduct2("zoneId", "displayName")(TimeZone.apply)
}

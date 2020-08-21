package commandcenter.command

import java.time.format.DateTimeFormatter
import java.time.{ OffsetDateTime, ZoneId, ZonedDateTime }

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.view.View
import commandcenter.{ CommandContext, TerminalType }
import io.circe.Decoder
import zio.{ TaskManaged, ZIO, ZManaged }

final case class WorldTimesCommand(commandNames: List[String], dateTimeFormat: String, zones: List[TimeZone])
    extends Command[Unit] {
  val commandType: CommandType = CommandType.WorldTimesCommand
  val title: String            = "World Times"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      now    = OffsetDateTime.now
      times  = zones.map(tz => WorldTimesResult(tz.zoneId, tz.displayName, now.atZoneSameInstant(tz.zoneId)))
    } yield List(
      Preview.unit.view(WorldTimesResults(dateTimeFormat, times, input.context)).score(Scores.high(input.context))
    )
}

object WorldTimesCommand extends CommandPlugin[WorldTimesCommand] {
  def make(config: Config): TaskManaged[WorldTimesCommand] =
    ZManaged.fromEither(
      for {
        commandNames   <- config.get[Option[List[String]]]("commandNames")
        dateTimeFormat <- config.get[String]("dateTimeFormat")
        zones          <- config.get[List[TimeZone]]("zones")
      } yield WorldTimesCommand(commandNames.getOrElse(List("time", "times")), dateTimeFormat, zones)
    )
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
  def intersperse[E](separator: E)(xs: List[E]): List[E] =
    (separator, xs) match {
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

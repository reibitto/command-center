package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.tools.Tools
import commandcenter.view.Rendered
import io.circe.Decoder
import zio.{ TaskManaged, ZIO, ZManaged }

import java.time.format.DateTimeFormatter
import java.time.{ ZoneId, ZonedDateTime }

final case class WorldTimesCommand(commandNames: List[String], dateTimeFormat: String, zones: List[TimeZone])
    extends Command[Unit] {
  val commandType: CommandType = CommandType.WorldTimesCommand
  val title: String            = "World Times"

  val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormat)
  val isoFormatter: DateTimeFormatter     = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX")

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      now   <- zio.clock.currentDateTime.!
      times  = zones.map(tz => WorldTimesResult(tz.zoneId, tz.displayName, now.atZoneSameInstant(tz.zoneId)))
      _      = times.map(time => displayFormatter.format(time.dateTime))
    } yield PreviewResults.fromIterable(
      times.map { time =>
        Preview.unit
          .score(Scores.high(input.context))
          .rendered(
            Rendered.Ansi(
              fansi.Color.Cyan(time.displayName) ++ fansi.Str(" ") ++ fansi.Str(displayFormatter.format(time.dateTime))
            )
          )
          .onRun(Tools.setClipboard(isoFormatter.format(time.dateTime)))
      }
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

final case class WorldTimesResults(dateTimeFormat: String, results: List[WorldTimesResult], context: CommandContext)

final case class WorldTimesResult(zone: ZoneId, displayName: String, dateTime: ZonedDateTime)

final case class TimeZone(zoneId: ZoneId, name: Option[String]) {
  def displayName: String = name.getOrElse(zoneId.getId)
}

object TimeZone {
  implicit val decoder: Decoder[TimeZone] = Decoder.forProduct2("zoneId", "displayName")(TimeZone.apply)
}

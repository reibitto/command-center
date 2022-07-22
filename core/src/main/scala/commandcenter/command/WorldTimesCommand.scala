package commandcenter.command

import com.typesafe.config.Config
import commandcenter.config.Decoders.*
import commandcenter.tools.Tools
import commandcenter.util.TimeZones
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import io.circe.Decoder
import org.ocpsoft.prettytime.nlp.PrettyTimeParser
import zio.{IO, Scope, URIO, ZIO}

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.jdk.CollectionConverters.*

final case class WorldTimesCommand(
  commandNames: List[String],
  dateTimeFormat: DateTimeFormatter,
  dateTimeDetailedFormat: DateTimeFormatter,
  dateTimeWithZoneFormat: DateTimeFormatter,
  zones: List[TimeZone]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.WorldTimesCommand
  val title: String = "World Times"

  val isoFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX")

  val parser = new PrettyTimeParser()

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      previews <- if (input.rest.isEmpty) {
                    configuredZones(input.context)
                  } else {
                    input.rest.split("@|>|\\bat\\b|\\bto\\b") match {
                      case Array(dateTimeString, timeZoneString) =>
                        (
                          parser.parse(dateTimeString.trim).asScala.headOption,
                          TimeZones.get(timeZoneString.trim)
                        ) match {
                          case (Some(date), Some(zone)) =>
                            ZIO.succeed(
                              timeToSpecificZone(
                                date.toInstant.atZone(ZoneId.systemDefault()),
                                zone,
                                input.context
                              )
                            )

                          case (Some(date), None) =>
                            ZIO.succeed(
                              timeToAllZones(
                                date.toInstant.atZone(ZoneId.systemDefault()),
                                input.context
                              )
                            )

                          case _ =>
                            configuredZones(input.context)
                        }

                      case _ =>
                        parser.parse(input.rest).asScala.headOption match {
                          case Some(date) =>
                            ZIO.succeed(
                              timeToAllZones(
                                date.toInstant.atZone(ZoneId.systemDefault()),
                                input.context
                              )
                            )
                          case None =>
                            configuredZones(input.context)
                        }

                    }

                  }
    } yield previews

  def timeToAllZones(dateTimeFrom: ZonedDateTime, context: CommandContext): PreviewResults[Unit] =
    PreviewResults.fromIterable(zones.map { zone =>
      val time = dateTimeFrom.withZoneSameInstant(zone.zoneId)

      Preview.unit
        .score(Scores.high(context))
        .rendered(
          Rendered.Ansi(
            fansi.Color.Cyan(s"${zone.displayName}") ++ fansi.Str(" ") ++ fansi.Str(dateTimeDetailedFormat.format(time))
          )
        )
    })

  def timeToSpecificZone(
    dateTimeFrom: ZonedDateTime,
    zoneTo: ZoneId,
    context: CommandContext
  ): PreviewResults[Unit] = {
    val time = dateTimeFrom.withZoneSameInstant(zoneTo)

    PreviewResults.one(
      Preview.unit
        .score(Scores.high(context))
        .rendered(
          Rendered.Ansi(
            fansi.Color.Cyan(s"${dateTimeWithZoneFormat.format(dateTimeFrom)}") ++ fansi.Str(" is ") ++
              fansi.Color.Green(dateTimeWithZoneFormat.format(time))
          )
        )
    )
  }

  def configuredZones(context: CommandContext): URIO[Any, PreviewResults[Unit]] =
    for {
      now <- zio.Clock.currentDateTime.map(_.toZonedDateTime)
      times = zones.map(tz => WorldTimesResult(tz.zoneId, tz.displayName, now.withZoneSameInstant(tz.zoneId)))
      _ = times.map(time => dateTimeFormat.format(time.dateTime))
    } yield PreviewResults.fromIterable(times.map { time =>
      Preview.unit
        .score(Scores.high(context))
        .rendered(
          Rendered.Ansi(
            fansi.Color.Cyan(time.displayName) ++ fansi.Str(" ") ++ fansi.Str(dateTimeFormat.format(time.dateTime))
          )
        )
        .onRun(Tools.setClipboard(isoFormat.format(time.dateTime)))
    })
}

object WorldTimesCommand extends CommandPlugin[WorldTimesCommand] {

  def make(config: Config): IO[CommandPluginError, WorldTimesCommand] =
    for {
      commandNames           <- config.getZIO[Option[List[String]]]("commandNames")
      dateTimeFormat         <- config.getZIO[Option[DateTimeFormatter]]("dateTimeFormat")
      dateTimeDetailedFormat <- config.getZIO[Option[DateTimeFormatter]]("dateTimeDetailedFormat")
      dateTimeWithZoneFormat <- config.getZIO[Option[DateTimeFormatter]]("dateTimeWithZoneFormat")
      zones                  <- config.getZIO[List[TimeZone]]("zones")
    } yield WorldTimesCommand(
      commandNames.getOrElse(List("time", "times")),
      dateTimeFormat.getOrElse(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)),
      dateTimeDetailedFormat.getOrElse(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)),
      dateTimeWithZoneFormat.getOrElse(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)),
      zones
    )
}

final case class WorldTimesResult(zone: ZoneId, displayName: String, dateTime: ZonedDateTime)

final case class TimeZone(zoneId: ZoneId, name: Option[String]) {
  def displayName: String = name.getOrElse(zoneId.getId)
}

object TimeZone {
  implicit val decoder: Decoder[TimeZone] = Decoder.forProduct2("zoneId", "displayName")(TimeZone.apply)
}

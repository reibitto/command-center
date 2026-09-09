package commandcenter.command

import com.typesafe.config.Config
import commandcenter.config.Decoders.*
import commandcenter.tools.Tools
import commandcenter.util.TimeZones
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import fansi.{Color, Str}
import io.circe.Decoder
import org.ocpsoft.prettytime.nlp.PrettyTimeParser
import zio.*

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.jdk.CollectionConverters.*
import scala.util.Try

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

  /** Splits "<date/time> at|to|@|> <zone>" into its two parts. Limited to 2
    * splits so that a stray second occurrence of "at"/"to" (e.g. inside the
    * date/time phrase itself) doesn't shift what ends up in the zone part.
    */
  private val connectorSeparator = "@|>|\\bat\\b|\\bto\\b"

  private def splitOnConnector(rest: String): Option[(String, String)] =
    rest.split(connectorSeparator, 2) match {
      case Array(head, tail) => Some((head.trim, tail.trim))
      case _                 => None
    }

  /** No zone identifier we recognize contains whitespace (IANA ids like
    * "America/New_York" and abbreviations/offsets like "JST"/"UTC+9" are always
    * a single token), so the last whitespace-delimited token is always a valid
    * place to look for one. This covers zones given with no connector word at
    * all, e.g. "8am est".
    */
  private def splitOnLastWhitespace(rest: String): Option[(String, String)] = {
    val i = rest.lastIndexWhere(_.isWhitespace)
    if (i < 0) None else Some((rest.substring(0, i).trim, rest.substring(i + 1).trim))
  }

  private def parseDateTime(text: String): Option[ZonedDateTime] =
    // PrettyTimeParser is a 3rd-party NLP parser; guard against it throwing on unexpected input rather than letting
    // it take down the whole command with a defect.
    Try(parser.parse(text).asScala.headOption).toOption.flatten.map(_.toInstant.atZone(ZoneId.systemDefault()))

  private def displayNameOf(zone: ZoneId): String =
    zones.find(_.zoneId == zone).map(_.displayName).getOrElse(zone.getId)

  private def unrecognized(rest: String, context: CommandContext): CommandError.ShowMessage =
    CommandError.ShowMessage(
      Rendered.Ansi(Color.Red(s"""Couldn't recognize "$rest" as a date/time or time zone""")),
      Scores.veryHigh(context)
    )

  private def unparseableDateTime(dateTimeString: String, context: CommandContext): CommandError.ShowMessage =
    CommandError.ShowMessage(
      Rendered.Ansi(Color.Red(s"""Couldn't parse "$dateTimeString" as a date/time""")),
      Scores.veryHigh(context)
    )

  private def unrecognizedZone(zoneString: String, context: CommandContext): CommandError.ShowMessage =
    CommandError.ShowMessage(
      Rendered.Ansi(Color.Red(s"""Couldn't recognize "$zoneString" as a time zone""")),
      Scores.veryHigh(context)
    )

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      rest = input.rest.trim
      previews <- if (rest.isEmpty) configuredZones(input.context) else parseTimeQuery(rest, input.context)
    } yield previews

  /** Handles all non-empty input to the command. Unlike an empty query (which
    * shows the configured zones' current time), any input here that can't be
    * understood is surfaced to the user as an error instead of silently falling
    * back to `configuredZones`/local time, which would otherwise look like the
    * query succeeded when it didn't.
    */
  def parseTimeQuery(rest: String, context: CommandContext): IO[CommandError, PreviewResults[Unit]] =
    TimeZones.get(rest) match {
      // The entire input is just a zone (e.g. "JST", "America/New_York") -> show its current time.
      case Some(zone) => timeInZoneNow(zone, context)
      case None       =>
        // Try splitting off a trailing zone two ways: via an explicit connector ("5pm at jst") and via the last
        // whitespace-delimited token ("8am est", with no connector at all). The connector-based split is tried
        // first since, when both apply, it yields the cleaner date/time string (e.g. "5pm" instead of "5pm at").
        val candidates = splitOnConnector(rest).toList ::: splitOnLastWhitespace(rest).toList

        candidates.collectFirst {
          case (dateTimeString, zoneString) if TimeZones.get(zoneString).isDefined =>
            (dateTimeString, TimeZones.get(zoneString).get)
        } match {
          case Some((dateTimeString, zone)) =>
            parseDateTime(dateTimeString) match {
              case Some(date) => ZIO.succeed(timeToSpecificZone(date, zone, context))
              case None       => ZIO.fail(unparseableDateTime(dateTimeString, context))
            }

          case None =>
            // Neither candidate's tail is a recognized zone. Either it was never meant to be one -- e.g. "next
            // friday at 5pm" splits into "next friday" / "5pm", and "5pm" is itself a valid time, so the split
            // was incidental and we fall back to parsing the whole phrase as one unit -- or the user genuinely
            // tried (and failed) to name a zone, e.g. "5pm at tokyo" or a typo'd "8am esst". In the latter case we
            // say so instead of silently dropping it, since prettytime would otherwise happily parse "5pm"/"8am"
            // and ignore the rest without any indication that the zone was never understood.
            candidates.headOption.map(_._2) match {
              case Some(zoneCandidate) if zoneCandidate.nonEmpty && parseDateTime(zoneCandidate).isEmpty =>
                ZIO.fail(unrecognizedZone(zoneCandidate, context))
              case _ =>
                wholeStringFallback(rest, context)
            }
        }
    }

  private def wholeStringFallback(rest: String, context: CommandContext): IO[CommandError, PreviewResults[Unit]] =
    parseDateTime(rest) match {
      case Some(date) => ZIO.succeed(timeToAllZones(date, context))
      case None       => ZIO.fail(unrecognized(rest, context))
    }

  def timeInZoneNow(zone: ZoneId, context: CommandContext): UIO[PreviewResults[Unit]] =
    for {
      now <- zio.Clock.currentDateTime.map(_.toZonedDateTime)
      time = now.withZoneSameInstant(zone)
    } yield PreviewResults.one(
      Preview.unit
        .score(Scores.veryHigh(context))
        .rendered(
          Rendered.Ansi(Color.Cyan(displayNameOf(zone)) ++ Str(" ") ++ Str(dateTimeDetailedFormat.format(time)))
        )
        .onRun(Tools.setClipboard(isoFormat.format(time)))
    )

  def timeToAllZones(dateTimeFrom: ZonedDateTime, context: CommandContext): PreviewResults[Unit] =
    PreviewResults.fromIterable(zones.map { zone =>
      val time = dateTimeFrom.withZoneSameInstant(zone.zoneId)

      Preview.unit
        .score(Scores.veryHigh(context))
        .rendered(
          Rendered.Ansi(
            Color.Cyan(s"${zone.displayName}") ++ Str(" ") ++ Str(dateTimeDetailedFormat.format(time))
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
        .score(Scores.veryHigh(context))
        .rendered(
          Rendered.Ansi(
            Color.Cyan(s"${dateTimeWithZoneFormat.format(dateTimeFrom)}") ++ Str(" is ") ++
              Color.Green(dateTimeWithZoneFormat.format(time))
          )
        )
    )
  }

  def configuredZones(context: CommandContext): UIO[PreviewResults[Unit]] =
    for {
      now <- zio.Clock.currentDateTime.map(_.toZonedDateTime)
      times = zones.map(tz => WorldTimesResult(tz.zoneId, tz.displayName, now.withZoneSameInstant(tz.zoneId)))
      _ = times.map(time => dateTimeFormat.format(time.dateTime))
    } yield PreviewResults.fromIterable(times.map { time =>
      Preview.unit
        .score(Scores.veryHigh(context))
        .rendered(
          Rendered.Ansi(
            Color.Cyan(time.displayName) ++ Str(" ") ++ Str(dateTimeFormat.format(time.dateTime))
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

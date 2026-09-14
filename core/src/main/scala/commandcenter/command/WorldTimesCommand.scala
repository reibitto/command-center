package commandcenter.command

import com.typesafe.config.Config
import commandcenter.config.Decoders.*
import commandcenter.tools.Tools
import commandcenter.util.TextFormats.*
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
import java.util.Locale
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

  private val connectorSeparator = "@|>|\\bat\\b|\\bto\\b|\\bin\\b"

  private def splitOnConnector(rest: String): Option[(String, String)] =
    rest.split(connectorSeparator, 2) match {
      case Array(head, tail) => Some((head.trim, tail.trim))
      case _                 => None
    }

  /** Only used to produce a best-effort guess for error messages once every
    * real attempt at finding a zone (see `trailingZone`) has already failed.
    */
  private def splitOnLastWhitespace(rest: String): Option[(String, String)] = {
    val i = rest.lastIndexWhere(_.isWhitespace)
    if (i < 0) None else Some((rest.substring(0, i).trim, rest.substring(i + 1).trim))
  }

  /** Finds a recognized zone at the end of `rest` with no connector word (e.g.
    * "8am est" or "8am los angeles"), by trying progressively longer trailing
    * whitespace-delimited spans, longest first.
    */
  private def trailingZone(rest: String): Option[(String, ZoneId)] = {
    val words = rest.split("\\s+").toIndexedSeq
    (1 until words.length).reverseIterator
      .map(n => (words.dropRight(n).mkString(" "), words.takeRight(n).mkString(" ")))
      .flatMap { case (head, tail) => resolveZone(tail).map(head -> _) }
      .nextOption()
  }

  private def resolveZone(name: String): Option[ZoneId] = {
    val normalized = name.trim.toLowerCase(Locale.ENGLISH)
    configuredZoneAliases.get(normalized).orElse(TimeZones.get(name))
  }

  private lazy val configuredZoneAliases: Map[String, ZoneId] =
    zones.flatMap(tz => tz.aliases.map(_.trim.toLowerCase(Locale.ENGLISH) -> tz.zoneId)).toMap

  private def parseDateTime(text: String, zone: ZoneId = ZoneId.systemDefault()): Option[ZonedDateTime] =
    // PrettyTimeParser is a 3rd-party NLP parser; guard against it throwing on unexpected input rather than letting
    // it take down the whole command with a defect.
    Try(parser.parse(text).asScala.headOption).toOption.flatten
      .map(_.toInstant.atZone(ZoneId.systemDefault()).toLocalDateTime.atZone(zone))

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
    resolveZone(rest) match {
      // The entire input is just a zone (e.g. "JST", "America/New_York") -> show its current time.
      case Some(zone) => timeInZoneNow(zone, context)
      case None       =>
        recognizedZoneTail(splitOnConnector(rest)) match {
          // "<time> [<source zone>] (at|to|in|@|>) <target zone>".
          case Some((beforeConnector, targetZone)) =>
            trailingZone(beforeConnector) match {
              // The phrase before the connector itself ends in a recognized zone.
              case Some((dateTimeString, sourceZone)) =>
                parseDateTime(dateTimeString, sourceZone) match {
                  case Some(date) => timeToSpecificZone(date, targetZone, context)
                  case None       => ZIO.fail(unparseableDateTime(dateTimeString, context))
                }

              // No explicit source (e.g. "5pm at jst") so it's implicitly local.
              case None =>
                parseDateTime(beforeConnector) match {
                  case Some(date) => timeToSpecificZone(date, targetZone, context)
                  case None       => ZIO.fail(unparseableDateTime(beforeConnector, context))
                }
            }

          case None =>
            // No connector, or its tail isn't a recognized zone. Fall back to a bare trailing zone with no
            // connector at all ("8am est"), which names a source rather than a target.
            trailingZone(rest) match {
              case Some((dateTimeString, sourceZone)) =>
                parseDateTime(dateTimeString, sourceZone) match {
                  case Some(date) => timeToAllZones(date, context)
                  case None       => ZIO.fail(unparseableDateTime(dateTimeString, context))
                }

              case None =>
                // Neither candidate's tail is a recognized zone.
                val candidates = splitOnConnector(rest).toList ::: splitOnLastWhitespace(rest).toList
                candidates.headOption.map(_._2) match {
                  case Some(zoneCandidate) if zoneCandidate.nonEmpty && parseDateTime(zoneCandidate).isEmpty =>
                    ZIO.fail(unrecognizedZone(zoneCandidate, context))
                  case _ =>
                    wholeStringFallback(rest, context)
                }
            }
        }
    }

  private def recognizedZoneTail(split: Option[(String, String)]): Option[(String, ZoneId)] =
    split.flatMap { case (head, zoneString) => resolveZone(zoneString).map(head -> _) }

  private def wholeStringFallback(rest: String, context: CommandContext): IO[CommandError, PreviewResults[Unit]] =
    parseDateTime(rest) match {
      case Some(date) => timeToAllZones(date, context)
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

  def timeToAllZones(dateTimeFrom: ZonedDateTime, context: CommandContext): UIO[PreviewResults[Unit]] =
    zio.Clock.currentDateTime.map(_.toZonedDateTime).map { now =>
      val offset = WorldTimesCommand.relativeOffset(now, dateTimeFrom)

      PreviewResults.fromIterable(zones.map { zone =>
        val time = dateTimeFrom.withZoneSameInstant(zone.zoneId)

        Preview.unit
          .score(Scores.veryHigh(context))
          .rendered(
            Rendered.Ansi(
              Color.Cyan(s"${zone.displayName}") ++ Str(" ") ++ Str(dateTimeDetailedFormat.format(time)) ++ offset
            )
          )
      })
    }

  def timeToSpecificZone(
      dateTimeFrom: ZonedDateTime,
      zoneTo: ZoneId,
      context: CommandContext
  ): UIO[PreviewResults[Unit]] =
    zio.Clock.currentDateTime.map(_.toZonedDateTime).map { now =>
      val time = dateTimeFrom.withZoneSameInstant(zoneTo)

      PreviewResults.one(
        Preview.unit
          .score(Scores.veryHigh(context))
          .rendered(
            Rendered.Ansi(
              Color.Cyan(s"${dateTimeWithZoneFormat.format(dateTimeFrom)}") ++ Str(" is ") ++
                Color.Green(dateTimeWithZoneFormat.format(time)) ++ WorldTimesCommand.relativeOffset(now, dateTimeFrom)
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

  /** Renders how far `target` is from `now`, e.g. " (+3.5 hours)" or " (-45.0
    * minutes)", green for a future time and red for a past one. Meant to be
    * appended to a converted-time display so it's obvious the shown time isn't
    * "now" but a specifically requested time.
    */
  def relativeOffset(now: ZonedDateTime, target: ZonedDateTime): Str = {
    val diff = java.time.Duration.between(now.toInstant, target.toInstant)
    val isPast = diff.isNegative
    val magnitude = zio.Duration.fromJava(if (isPast) diff.negated() else diff)
    val text = s"${if (isPast) "-" else "+"}${magnitude.pretty}"

    Str(" (") ++ (if (isPast) Color.Red(text) else Color.Green(text)) ++ Str(")")
  }

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

final case class TimeZone(zoneId: ZoneId, name: Option[String], alternateZoneIds: List[String] = Nil) {
  def displayName: String = name.getOrElse(zoneId.getId)

  /** Every string that should resolve to this zone when typed by hand: its
    * display name plus whatever aliases were configured explicitly.
    */
  def aliases: List[String] = displayName :: alternateZoneIds
}

object TimeZone {

  implicit val decoder: Decoder[TimeZone] =
    Decoder.forProduct3("zoneId", "displayName", "alternateZoneIds") {
      (zoneId: ZoneId, name: Option[String], alternateZoneIds: Option[List[String]]) =>
        TimeZone(zoneId, name, alternateZoneIds.getOrElse(Nil))
    }
}

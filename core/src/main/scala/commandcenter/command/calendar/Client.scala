package commandcenter.command.calendar

import commandcenter.command.CalendarCommand.Formats
import fansi.{ Color, Str }
import zio.Task

import java.time.{ LocalDate, LocalTime }

trait Client {
  def list(request: ListRequest): Task[ListResponse]

  def insert(request: InsertRequest): Task[Unit]
}

case class Event(
  summary: String,
  description: Option[String] = None,
  location: Option[String] = None,
  startDateTime: EventDate,
  endDateTime: Option[EventDate] = None
) {
  def toString(formats: Formats): String = {
    def elide(text: String, maxLength: Int) = {
      val ellipsis = "..."
      if (text.length <= maxLength) text
      else text.substring(0, maxLength - ellipsis.length) + ellipsis
    }
    def formatSummary: Str                             = summary
    def formatDescription(description: String): Str    = Color.Cyan("Description: ") ++ elide(description, 50)
    def formatLocation(location: String): Str          = Color.Cyan("Location: ") ++ location
    def formatStartDateTime: Str                       = Color.Cyan("Start: ") ++ startDateTime.toString(formats)
    def formatEndDateTime(endDateTime: EventDate): Str = Color.Cyan("End: ") ++ endDateTime.toString(formats)

    val lines = List(
      Some(formatSummary),
      description.map(formatDescription),
      location.map(formatLocation),
      Some(formatStartDateTime),
      endDateTime.map(formatEndDateTime)
    )
    lines.collect { case Some(value) => value }.mkString("\n")
  }
}

case class EventDate(date: LocalDate, time: Option[LocalTime]) {
  def toString(formats: Formats): String =
    time
      .map(date.atTime)
      .fold(date.format(formats.dateFormatter))(dt =>
        s"${dt.format(formats.dateFormatter)} ${dt.format(formats.timeFormatter)}"
      )
}

case class ListRequest(maxResults: Int)

case class ListResponse(events: List[Event])

case class InsertRequest(event: Event)

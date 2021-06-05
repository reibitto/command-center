package commandcenter.command.calendar

import commandcenter.command.CalendarCommand.Formats
import fastparse.Parsed.Success
import fastparse.ScalaWhitespace._
import fastparse._

import java.time.{ LocalDate, ZoneId }

final class CalendarFreeTextParser(formats: Formats) {
  def parseText(input: String): Option[InsertRequest] =
    parse(input, parseEvent(_)) match {
      case Success(event, _) => Some(InsertRequest(event))
      case _                 => None
    }

  private def parseEvent[_: P]: P[Event] =
    P(summary ~ "@" ~ dateTime ~ End).map { case (summary, dateTime) =>
      Event(summary, startDateTime = dateTime)
    }

  private def summary[_: P]: P[String] = {
    import fastparse.NoWhitespace._

    P(!"@" ~ AnyChar).rep(1).!.map(_.trim)
  }

  private def dateTime[_: P]: P[EventDate] = {
    def dt = P(AnyChar.rep(1).!).map(_.trim)

    dt.flatMap { dateTimeString =>
      formats
        .parseDateTime(dateTimeString)
        .map(dateTime => Pass(EventDate(dateTime.toLocalDate, Some(dateTime.toLocalTime))))
        .orElse(
          formats
            .parseDate(dateTimeString)
            .map(date => Pass(EventDate(date, None)))
        )
        .orElse(
          formats
            .parseTime(dateTimeString)
            .map(time => Pass(EventDate(LocalDate.now(ZoneId.systemDefault()), Some(time))))
        )
        .getOrElse(Fail)
    }
  }
}

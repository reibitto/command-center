package commandcenter.command.calendar

import cats.data.Validated
import cats.data.Validated.Valid
import cats.implicits.catsSyntaxTuple4Semigroupal
import com.monovore.decline
import com.monovore.decline.{ Command, Opts }
import commandcenter.command.CalendarCommand.Formats

final class CalendarCommandLineParser(formats: Formats) {
  def parseCommand: Command[Product] = {
    val listMaxResults = Opts.argument[Int]("maxResults").withDefault(3)
    val listCommand    = decline.Command("list", "List next events on calendar")(listMaxResults.map(ListRequest))

    val insertSummary     = Opts.argument[String]("summary")
    val insertDescription = Opts.option[String]("desc", "description").orNone
    val insertLocation    = Opts.option[String]("loc", "location", "l").orNone
    val insertStartDate0  = Opts.option[String]("date", "start date", "d")
    val insertStartTime0  = Opts.option[String]("time", "start time", "t").orNone
    val insertEndDate0    = Opts.option[String]("enddate", "end date").orNone
    val insertEndTime0    = Opts.option[String]("endtime", "end time").orNone
    val insertDateTimes = {
      val dateErrorMessage = (s: String) => {
        val dayOffsets = formats.dayOffsets.keys.map(key => s"'$key'").mkString("[", ", ", "]")
        s"$s date should have pattern ${formats.dateFormat} or be one of $dayOffsets"
      }
      val timeErrorMessage = (s: String) => s"$s time should have pattern ${formats.timeFormat}"
      (insertStartDate0, insertStartTime0, insertEndDate0, insertEndTime0).tupled.mapValidated {
        case (_, Some(_), Some(_), None) =>
          Validated.invalidNel("If you specify start time, you also have to specify end time")
        case (_, _, None, Some(_))       => Validated.invalidNel("If you specify end time, you also have to specify end date")
        case (_, None, _, Some(_))       => Validated.invalidNel("For all-day events you must not specify end time")
        case (sd, st, ed, et)            =>
          val sdv = formats.parseDate(sd) match {
            case Some(dt) => Validated.valid(dt)
            case _        => Validated.invalidNel(dateErrorMessage("start"))
          }
          val stv = st.map(formats.parseTime) match {
            case Some(Some(t)) => Validated.valid(Some(t))
            case Some(_)       => Validated.invalidNel(timeErrorMessage("start"))
            case _             => Validated.valid(None)
          }
          val edv = ed.map(formats.parseDate) match {
            case Some(Some(d)) => Validated.valid(Some(d))
            case Some(_)       => Validated.invalidNel(dateErrorMessage("end"))
            case _             => Validated.valid(None)
          }
          val etv = et.map(formats.parseTime) match {
            case Some(Some(t)) => Validated.valid(Some(t))
            case Some(_)       => Validated.invalidNel(timeErrorMessage("end"))
            case _             => Validated.valid(None)
          }

          (sdv, stv, edv, etv) match {
            case (Valid(sd), Valid(Some(st)), Valid(Some(ed)), Valid(Some(et))) if sd.isEqual(ed) && et.isBefore(st) =>
              Validated.invalidNel("End time before start time")
            case (Valid(sd), _, Valid(Some(ed)), _) if ed.isBefore(sd)                                               =>
              Validated.invalidNel("End date before start date")
            case _                                                                                                   => (sdv, stv, edv, etv).tupled
          }
      }
    }
    val insertCommand     = decline.Command("insert", "Add event to calendar")(
      (insertSummary, insertDescription, insertLocation, insertDateTimes).mapN { case (s, d, l, (sd, st, ed, et)) =>
        InsertRequest(Event(s, d, l, EventDate(sd, st), ed.map(EventDate(_, et))))
      }
    )

    val opts = Opts.subcommands(listCommand, insertCommand)
    decline.Command("calendar", "Calendar commands")(opts)
  }
}

package commandcenter.command.calendar

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.{ Event => GEvent, EventDateTime => GEventDateTime }
import com.google.api.services.calendar.{ Calendar, CalendarScopes }
import com.google.auth.Credentials
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import commandcenter.command.calendar.GoogleClient.{
  eventDateToGoogleEventDateTime,
  getCalendarService,
  getCredentials,
  googleEventDateTimeToEventDate
}
import zio.Task

import java.time.ZoneId
import java.util.Date

final case class GoogleClient(calendarId: String) extends Client {
  import scala.jdk.CollectionConverters._

  override def list(request: ListRequest): Task[ListResponse] = for {
    credentials  <- getCredentials
    googleEvents <- Task(
                      getCalendarService(credentials)
                        .events()
                        .list(calendarId)
                        .setMaxResults(request.maxResults)
                        .setTimeMin(new DateTime(System.currentTimeMillis()))
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .execute()
                    )
  } yield {
    val events = googleEvents.getItems.asScala.toList.map(googleEvent =>
      Event(
        googleEvent.getSummary,
        Option(googleEvent.getDescription),
        Option(googleEvent.getLocation),
        googleEventDateTimeToEventDate(googleEvent.getStart),
        Some(googleEventDateTimeToEventDate(googleEvent.getEnd))
      )
    )
    ListResponse(events)
  }

  override def insert(request: InsertRequest): Task[Unit] = {
    val event = request.event
    for {
      credentials <- getCredentials
      startDate    = eventDateToGoogleEventDateTime(event.startDateTime)
      endDate      = event.endDateTime.map(eventDateToGoogleEventDateTime).getOrElse(startDate)
      googleEvent  = new GEvent()
                       .setSummary(event.summary)
                       .setDescription(event.description.orNull)
                       .setLocation(event.location.orNull)
                       .setStart(startDate)
                       .setEnd(endDate)
      _           <- Task(getCalendarService(credentials).events().insert(calendarId, googleEvent).execute())
    } yield ()
  }
}

object GoogleClient {
  import scala.jdk.CollectionConverters._

  private val credentialsFilePath = "calendar/google/credentials.json"

  private val scopes = List(
    CalendarScopes.CALENDAR_READONLY,
    CalendarScopes.CALENDAR_EVENTS
  ).asJavaCollection

  private val credentials = Task(
    ServiceAccountCredentials
      .fromStream(
        ClassLoader.getSystemResourceAsStream(credentialsFilePath)
      )
      .createScoped(scopes)
  )

  def getCredentials: Task[Credentials] =
    credentials.map { cred =>
      cred.refreshIfExpired()
      cred
    }

  def getCalendarService(credentials: Credentials): Calendar =
    new Calendar.Builder(
      GoogleNetHttpTransport.newTrustedTransport(),
      JacksonFactory.getDefaultInstance,
      new HttpCredentialsAdapter(credentials)
    )
      .setApplicationName("CommandCenter calendar")
      .build()

  def googleEventDateTimeToEventDate(edt: GEventDateTime): EventDate = {
    val googleDateTime = Option(edt.getDate).getOrElse(edt.getDateTime)
    val zonedDateTime  = new Date(googleDateTime.getValue).toInstant.atZone(ZoneId.systemDefault())
    val time           = Option(edt.getDateTime).map(_ => zonedDateTime.toLocalTime)
    EventDate(zonedDateTime.toLocalDate, time)
  }

  def eventDateToGoogleEventDateTime(ed: EventDate): GEventDateTime = {
    val edt = new GEventDateTime()
    ed.time.fold(
      edt.setDate(new DateTime(true, ed.date.toEpochDay * 86400000, 0))
    )(time => edt.setDateTime(new DateTime(Date.from(ed.date.atTime(time).atZone(ZoneId.systemDefault()).toInstant))))
  }
}

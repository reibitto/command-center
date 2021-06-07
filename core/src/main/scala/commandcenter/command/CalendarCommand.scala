package commandcenter.command

import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.command.CalendarCommand.Formats
import commandcenter.command.calendar._
import commandcenter.view.DefaultView
import fansi.Str
import io.circe
import zio.{ TaskManaged, UIO, ZIO, ZManaged }

import java.time.format.DateTimeFormatter
import java.time.{ LocalDate, LocalDateTime, LocalTime, ZoneId }
import scala.util.Try

final case class CalendarCommand(override val commandNames: List[String], client: Client, formats: Formats)
    extends Command[Unit] {
  val commandType: CommandType               = CommandType.CalendarCommand
  val title: String                          = "Calendar"
  val freeTextParser: CalendarFreeTextParser = new CalendarFreeTextParser(formats)
  val cliCommand: decline.Command[Product]   = new CalendarCommandLineParser(formats).parseCommand

  override def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    previewFreeText(searchInput) <> previewCommandLine(searchInput)

  private def previewFreeText(searchInput: SearchInput): ZIO[Any, CommandError, PreviewResults[Unit]] =
    for {
      request <- ZIO.fromOption(freeTextParser.parseText(searchInput.input)).orElseFail(CommandError.NotApplicable)
    } yield previewInsertRequest(request, searchInput.context)

  private def previewCommandLine(searchInput: SearchInput): ZIO[Any, CommandError, PreviewResults[Unit]] =
    for {
      input       <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed       = cliCommand.parse(input.args)
      previewItem <- ZIO
                       .fromEither(parsed)
                       .foldM(
                         help => UIO(HelpMessage.formatted(help)),
                         {
                           case request: ListRequest   => client.list(request).mapError(CommandError.UnexpectedException)
                           case request: InsertRequest => UIO(request)
                         }
                       )
    } yield previewItem match {
      case ListResponse(events)   =>
        PreviewResults.fromIterable(
          events.map { event =>
            Preview.unit
              .score(Scores.high(input.context))
              .view(DefaultView("Calendar event", event.toString(formats)))
          }
        )
      case request: InsertRequest =>
        previewInsertRequest(request, input.context)
      case help: Str              =>
        PreviewResults.one(
          Preview.unit
            .score(Scores.high(input.context))
            .view(DefaultView(title, help))
        )
    }

  private def previewInsertRequest(request: InsertRequest, context: CommandContext) = {
    val run = for {
      _ <- client.insert(request)
    } yield ()
    PreviewResults.one(
      Preview.unit
        .onRun(run)
        .score(Scores.high(context))
        .view(DefaultView("Add calendar event", request.event.toString(formats)))
    )
  }
}

object CalendarCommand extends CommandPlugin[CalendarCommand] {

  override def make(config: Config): TaskManaged[CalendarCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
        client       <- getClient(config.getConfig("client"))
        formats      <- getFormats(config.getConfig("formats"))
      } yield CalendarCommand(commandNames.getOrElse(List("calendar", "cal")), client, formats)
    )

  private def getClient(config: Config): Either[Exception, Client] =
    for {
      clientTypeString <- config.get[String]("type")
      clientType       <- ClientType.withNameEither(clientTypeString)
      client           <- clientType match {
                            case ClientType.Google => getGoogleClient(config)
                          }
    } yield client

  private def getGoogleClient(config: Config): Either[circe.Error, GoogleClient] =
    for {
      calendarId <- config.get[String]("calendarId")
    } yield GoogleClient(calendarId)

  private def getFormats(config: Config): Either[circe.Error, Formats] = for {
    dateFormat <- config.get[String]("dateFormat")
    timeFormat <- config.get[String]("timeFormat")
    dayOffsets <- config.get[Map[String, Int]]("dayOffsets")
  } yield Formats(dateFormat, timeFormat, dayOffsets)

  final case class Formats(dateFormat: String, timeFormat: String, dayOffsets: Map[String, Int]) {
    val dateFormatter: DateTimeFormatter     = DateTimeFormatter.ofPattern(dateFormat)
    val timeFormatter: DateTimeFormatter     = DateTimeFormatter.ofPattern(timeFormat)
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(s"$dateFormat $timeFormat")

    def parseDate(dateString: String): Option[LocalDate] =
      dayOffsets.get(dateString.toLowerCase) match {
        case Some(offset) => Some(getLocalDateByOffset(offset))
        case _            => Try(LocalDate.parse(dateString, dateFormatter)).toOption
      }

    def parseTime(timeString: String): Option[LocalTime] =
      Try(LocalTime.parse(timeString, timeFormatter)).toOption

    def parseDateTime(dateTimeString: String): Option[LocalDateTime] =
      dayOffsets.find { case (key, _) =>
        dateTimeString.startsWith(key)
      } match {
        case Some((key, offset)) =>
          parseTime(dateTimeString.stripPrefix(s"$key ")) match {
            case Some(time) =>
              val date = getLocalDateByOffset(offset)
              Some(LocalDateTime.of(date, time))
            case _          => None
          }
        case _                   => Try(LocalDateTime.parse(dateTimeString, dateTimeFormatter)).toOption
      }

    private def getLocalDateByOffset(offset: Int): LocalDate =
      LocalDate.now(ZoneId.systemDefault()).plusDays(offset)
  }
}

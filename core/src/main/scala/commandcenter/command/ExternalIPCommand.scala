package commandcenter.command

import commandcenter.CommandContext
import commandcenter.util.{ OS, ProcessUtil }
import io.circe.Decoder
import zio.ZIO
import zio.blocking.Blocking
import zio.process.{ Command => PCommand }

final case class ExternalIPCommand() extends Command[String] {
  val commandType: CommandType = CommandType.ExternalIPCommand

  val commandNames: List[String] = List("externalip")

  val title: String = "External IP"

  // TODO: Also support Windows (nslookup?). If there's no good solution, making an api.ipify.org request could work too.
  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Linux)

  override def keywordPreview(
    keyword: String,
    context: CommandContext
  ): ZIO[Blocking, CommandError, List[PreviewResult[String]]] =
    for {
      externalIP <- PCommand("dig", "+short", "myip.opendns.com", "@resolver1.opendns.com").string
                     .mapError(CommandError.UnexpectedException)
    } yield {
      List(
        Preview(externalIP)
          .score(Scores.high(context))
          .onRun(ProcessUtil.copyToClipboard(externalIP))
      )
    }
}

object ExternalIPCommand extends CommandPlugin[ExternalIPCommand] {
  implicit val decoder: Decoder[ExternalIPCommand] = Decoder.const(ExternalIPCommand())
}

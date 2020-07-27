package commandcenter.command

import commandcenter.CommandContext
import commandcenter.util.{ OS, ProcessUtil }
import io.circe.Decoder
import zio.ZIO
import zio.blocking._
import zio.process.{ Command => PCommand }

final case class LocalIPCommand() extends Command[String] {
  val commandType: CommandType = CommandType.LocalIPCommand

  val commandNames: List[String] = List("localip")

  val title: String = "Local IP"

  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Linux)

  override def keywordPreview(
    keyword: String,
    context: CommandContext
  ): ZIO[Blocking, CommandError, List[PreviewResult[String]]] =
    for {
      localIP <- PCommand("ipconfig", "getifaddr", "en0").string.mapError(CommandError.UnexpectedException)
    } yield {
      List(
        Preview(localIP)
          .onRun(ProcessUtil.copyToClipboard(localIP))
          .score(Scores.high(context))
      )
    }
}

object LocalIPCommand extends CommandPlugin[LocalIPCommand] {
  implicit val decoder: Decoder[LocalIPCommand] = Decoder.const(LocalIPCommand())
}

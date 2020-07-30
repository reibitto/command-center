package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.util.{ OS, ProcessUtil }
import io.circe.Decoder
import zio.ZIO
import zio.process.{ Command => PCommand }

final case class LocalIPCommand() extends Command[String] {
  val commandType: CommandType = CommandType.LocalIPCommand

  val commandNames: List[String] = List("localip")

  val title: String = "Local IP"

  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Linux)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input   <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      localIP <- PCommand("ipconfig", "getifaddr", "en0").string.bimap(CommandError.UnexpectedException, _.trim)
    } yield {
      List(
        Preview(localIP)
          .onRun(ProcessUtil.copyToClipboard(localIP))
          .score(Scores.high(input.context))
      )
    }
}

object LocalIPCommand extends CommandPlugin[LocalIPCommand] {
  implicit val decoder: Decoder[LocalIPCommand] = Decoder.const(LocalIPCommand())
}

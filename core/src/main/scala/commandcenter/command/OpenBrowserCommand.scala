package commandcenter.command

import commandcenter.CommandContext
import commandcenter.command.CommandError._
import commandcenter.util.ProcessUtil
import io.circe.Decoder
import zio.blocking.Blocking
import zio.{ IO, UIO, ZIO }

final case class OpenBrowserCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.OpenBrowserCommand

  val commandNames: List[String] = List.empty

  val title: String = "Open in Browser"

  override def inputPreview(
    input: String,
    context: CommandContext
  ): ZIO[Blocking, CommandError, List[PreviewResult[Unit]]] = {
    val startsWith = input.startsWith("http://") || input.startsWith("https://")

    // TODO: also check endsWith TLD + URL.isValid

    if (startsWith) {
      UIO(List(Preview.unit.onRun(ProcessUtil.openBrowser(input))))
    } else {
      IO.fail(NotApplicable)
    }
  }
}

object OpenBrowserCommand extends CommandPlugin[OpenBrowserCommand] {
  implicit val decoder: Decoder[OpenBrowserCommand] = Decoder.const(OpenBrowserCommand())
}

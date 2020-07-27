package commandcenter.command

import commandcenter.CommandContext
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ IO, UIO }

// TODO: Work in progress
final case class TerminalCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.TerminalCommand

  val commandNames: List[String] = List("$", ">")

  val title: String = "Terminal"

  override def prefixPreview(
    prefix: String,
    rest: String,
    context: CommandContext
  ): IO[CommandError, List[PreviewResult[Unit]]] =
    UIO(
      List(
        Preview.unit.view(DefaultView("Terminal", rest)).score(Scores.high(context))
      )
    )
}

object TerminalCommand extends CommandPlugin[TerminalCommand] {
  implicit val decoder: Decoder[TerminalCommand] = Decoder.const(TerminalCommand())
}

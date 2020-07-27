package commandcenter.command

import commandcenter.CommandContext
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ IO, UIO }

final case class ExitCommand() extends Command[CommandResult] {
  val commandType: CommandType = CommandType.ExitCommand

  val commandNames: List[String] = List("exit", "quit")

  val title: String = "Exit Command Center"

  override def keywordPreview(
    keyword: String,
    context: CommandContext
  ): IO[CommandError, List[PreviewResult[CommandResult]]] =
    UIO(List(Preview(CommandResult.Exit).view(DefaultView(title, "")).score(Scores.high(context))))
}

object ExitCommand extends CommandPlugin[ExitCommand] {
  implicit val decoder: Decoder[ExitCommand] = Decoder.const(ExitCommand())
}

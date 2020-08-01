package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO

final case class ExitCommand() extends Command[CommandResult] {
  val commandType: CommandType = CommandType.ExitCommand

  val commandNames: List[String] = List("exit", "quit")

  val title: String = "Exit Command Center"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[CommandResult]]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield List(Preview(CommandResult.Exit).view(DefaultView(title, "")).score(Scores.high(input.context)))
}

object ExitCommand extends CommandPlugin[ExitCommand] {
  implicit val decoder: Decoder[ExitCommand] = Decoder.const(ExitCommand())
}

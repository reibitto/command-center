package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO

// TODO: Work in progress
final case class TerminalCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.TerminalCommand

  val commandNames: List[String] = List("$", ">")

  val title: String = "Terminal"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
    } yield {
      List(Preview.unit.view(DefaultView("Terminal", input.rest)).score(Scores.high(input.context)))
    }
}

object TerminalCommand extends CommandPlugin[TerminalCommand] {
  implicit val decoder: Decoder[TerminalCommand] = Decoder.const(TerminalCommand())
}

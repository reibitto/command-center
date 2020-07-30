package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO

final case class ReloadCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ResizeCommand

  val commandNames: List[String] = List("reload")

  val title: String = "Reload Config"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield {
      List(
        Preview.unit
          .onRun(input.context.terminal.reload.ignore)
          .score(Scores.high(input.context))
          .view(DefaultView(title, ""))
      )
    }
}

object ReloadCommand extends CommandPlugin[ReloadCommand] {
  implicit val decoder: Decoder[ReloadCommand] = Decoder.const(ReloadCommand())
}

package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ UIO, ZIO }

final case class ReloadCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ResizeCommand

  val commandNames: List[String] = List("reload")

  val title: String = "Reload Config"

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    UIO(
      List(
        Preview.unit
          .onRun(context.terminal.reload.ignore)
          .score(Scores.high(context))
          .view(DefaultView(title, ""))
      )
    )
}

object ReloadCommand extends CommandPlugin[ReloadCommand] {
  implicit val decoder: Decoder[ReloadCommand] = Decoder.const(ReloadCommand())
}

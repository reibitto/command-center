package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.view.DefaultView
import zio.{ TaskManaged, ZIO, ZManaged }

final case class ReloadCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ResizeCommand

  val commandNames: List[String] = List("reload")

  val title: String = "Reload Config"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield List(
      Preview.unit
        .onRun(input.context.terminal.reload.orDie)
        .score(Scores.high(input.context))
        .view(DefaultView(title, ""))
    )
}

object ReloadCommand extends CommandPlugin[ReloadCommand] {
  def make(config: Config): TaskManaged[ReloadCommand] = ZManaged.succeed(ReloadCommand())
}

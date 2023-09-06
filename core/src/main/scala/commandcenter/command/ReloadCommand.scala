package commandcenter.command

import com.typesafe.config.Config
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.{IO, ZIO}

final case class ReloadCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ResizeCommand
  val title: String = "Reload Config"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield PreviewResults.one(
      Preview.unit
        .onRun(input.context.terminal.reload)
        .score(Scores.veryHigh(input.context))
        .rendered(Renderer.renderDefault(title, ""))
    )
}

object ReloadCommand extends CommandPlugin[ReloadCommand] {

  def make(config: Config): IO[CommandPluginError, ReloadCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield ReloadCommand(commandNames.getOrElse(List("reload")))
}

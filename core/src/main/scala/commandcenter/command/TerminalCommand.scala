package commandcenter.command

import com.typesafe.config.Config
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.*

// TODO: Work in progress
final case class TerminalCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.TerminalCommand
  val title: String = "Terminal"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    searchInput.prefixed.map { input =>
      PreviewResults.one(
        Preview.unit.rendered(Renderer.renderDefault("Terminal", input.rest)).score(Scores.veryHigh(input.context))
      )
    }
}

object TerminalCommand extends CommandPlugin[TerminalCommand] {

  def make(config: Config): IO[CommandPluginError, TerminalCommand] =
    commandNames(config, "$", ">").map(TerminalCommand(_))
}

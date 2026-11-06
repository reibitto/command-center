package commandcenter.command

import com.typesafe.config.Config
import commandcenter.event.KeyboardShortcut
import commandcenter.CCRuntime.Env
import zio.*

final case class ReloadCommand(commandNames: List[String], override val shortcuts: Set[KeyboardShortcut] = Set.empty)
    extends Command[Unit] {
  val commandType: CommandType = CommandType.ReloadCommand
  val title: String = "Reload Config"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    searchInput.keyword.map { input =>
      PreviewResults.one(
        Preview.unit
          .onRun(input.context.terminal.reload)
          .score(Scores.veryHigh(input.context))
      )
    }
}

object ReloadCommand extends CommandPlugin[ReloadCommand] {

  def make(config: Config): IO[CommandPluginError, ReloadCommand] =
    commandNames(config, "reload").zipWith(shortcuts(config))(ReloadCommand(_, _))
}

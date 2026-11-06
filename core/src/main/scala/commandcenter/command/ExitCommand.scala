package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import zio.*

final case class ExitCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ExitCommand
  val title: String = "Exit Command Center"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    searchInput.keyword.map { input =>
      PreviewResults.one(
        Preview.unit
          .score(Scores.veryHigh(input.context))
          .runOption(RunOption.Exit)
      )
    }
}

object ExitCommand extends CommandPlugin[ExitCommand] {

  def make(config: Config): IO[CommandPluginError, ExitCommand] =
    commandNames(config, "exit", "quit").map(ExitCommand(_))
}

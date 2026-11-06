package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCConfig
import commandcenter.CCRuntime.Env
import zio.*

import java.awt.Desktop

final case class ConfigCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ConfigCommand
  val title: String = "Command Center Config"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- searchInput.keyword
      file  <- CCConfig.defaultConfigFile
    } yield {
      val run = ZIO.attemptBlocking(Desktop.getDesktop.open(file))

      PreviewResults.one(
        Preview.unit
          .score(Scores.veryHigh(input.context))
          .onRun(run)
      )
    }
}

object ConfigCommand extends CommandPlugin[ConfigCommand] {

  def make(config: Config): IO[CommandPluginError, ConfigCommand] =
    commandNames(config, "config").map(ConfigCommand(_))
}

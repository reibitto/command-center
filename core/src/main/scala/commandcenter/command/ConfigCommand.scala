package commandcenter.command

import com.typesafe.config.Config
import commandcenter.view.Renderer
import commandcenter.CCConfig
import commandcenter.CCRuntime.Env
import zio.*

import java.awt.Desktop

final case class ConfigCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ConfigCommand
  val title: String = "Command Center Config"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      file  <- CCConfig.defaultConfigFile
    } yield {
      val run = ZIO.attempt(Desktop.getDesktop.open(file))

      PreviewResults.one(
        Preview.unit
          .rendered(Renderer.renderDefault(title, ""))
          .score(Scores.veryHigh(input.context))
          .onRun(run)
      )
    }
}

object ConfigCommand extends CommandPlugin[ConfigCommand] {

  def make(config: Config): IO[CommandPluginError, ConfigCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield ConfigCommand(commandNames.getOrElse(List("config")))
}

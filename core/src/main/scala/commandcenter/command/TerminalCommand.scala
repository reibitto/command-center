package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.view.DefaultView
import zio.{ TaskManaged, ZIO, ZManaged }

// TODO: Work in progress
final case class TerminalCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.TerminalCommand
  val title: String            = "Terminal"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
    } yield PreviewResults.one(Preview.unit.view(DefaultView("Terminal", input.rest)).score(Scores.high(input.context)))
}

object TerminalCommand extends CommandPlugin[TerminalCommand] {
  def make(config: Config): TaskManaged[TerminalCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield TerminalCommand(commandNames.getOrElse(List("$", ">")))
    )
}

package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.view.DefaultView
import zio.{ TaskManaged, ZIO, ZManaged }

final case class ExitCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ExitCommand
  val title: String            = "Exit Command Center"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield PreviewResults.one(
      Preview.unit.view(DefaultView(title, "")).score(Scores.high(input.context)).runOption(RunOption.Exit)
    )
}

object ExitCommand extends CommandPlugin[ExitCommand] {
  def make(config: Config): TaskManaged[ExitCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield ExitCommand(commandNames.getOrElse(List("exit", "quit")))
    )
}

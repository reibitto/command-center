package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.view.DefaultView
import zio.{ TaskManaged, ZIO, ZManaged }

final case class ReloadCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ResizeCommand
  val title: String            = "Reload Config"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield PreviewResults.one(
      Preview.unit
        .onRun(input.context.terminal.reload.!)
        .score(Scores.high(input.context))
        .view(DefaultView(title, ""))
    )
}

object ReloadCommand extends CommandPlugin[ReloadCommand] {
  def make(config: Config): TaskManaged[ReloadCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield ReloadCommand(commandNames.getOrElse(List("reload")))
    )
}

package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.view.DefaultView
import zio.{ TaskManaged, ZIO, ZManaged }

final case class ExitCommand() extends Command[CommandResult] {
  val commandType: CommandType = CommandType.ExitCommand

  val commandNames: List[String] = List("exit", "quit")

  val title: String = "Exit Command Center"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[CommandResult]]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield List(Preview(CommandResult.Exit).view(DefaultView(title, "")).score(Scores.high(input.context)))
}

object ExitCommand extends CommandPlugin[ExitCommand] {
  def make(config: Config): TaskManaged[ExitCommand] = ZManaged.succeed(ExitCommand())
}

package commandcenter.command

import com.typesafe.config.Config
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.{IO, ZIO}

import java.util.UUID

final case class UUIDCommand(commandNames: List[String]) extends Command[UUID] {
  val commandType: CommandType = CommandType.UUIDCommand
  val title: String = "UUID"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[UUID]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      uuid = UUID.randomUUID()
    } yield PreviewResults.one(
      Preview(uuid).onRun(Tools.setClipboard(uuid.toString)).score(Scores.high(input.context))
    )
}

object UUIDCommand extends CommandPlugin[UUIDCommand] {

  def make(config: Config): IO[CommandPluginError, UUIDCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield UUIDCommand(commandNames.getOrElse(List("uuid", "guid")))
}

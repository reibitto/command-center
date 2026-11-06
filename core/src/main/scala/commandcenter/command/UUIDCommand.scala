package commandcenter.command

import com.typesafe.config.Config
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.*

import java.util.UUID

final case class UUIDCommand(commandNames: List[String]) extends Command[UUID] {
  val commandType: CommandType = CommandType.UUIDCommand
  val title: String = "UUID"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[UUID]] =
    for {
      input <- searchInput.keyword
      uuid = UUID.randomUUID()
    } yield PreviewResults.one(
      Preview(uuid).onRun(Tools.setClipboard(uuid.toString)).score(Scores.veryHigh(input.context))
    )
}

object UUIDCommand extends CommandPlugin[UUIDCommand] {

  def make(config: Config): IO[CommandPluginError, UUIDCommand] =
    commandNames(config, "uuid", "guid").map(UUIDCommand(_))
}

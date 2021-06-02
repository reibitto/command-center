package commandcenter.command

import java.util.UUID

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools
import zio.{ TaskManaged, ZIO, ZManaged }

final case class UUIDCommand(commandNames: List[String]) extends Command[UUID] {
  val commandType: CommandType = CommandType.UUIDCommand
  val title: String            = "UUID"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[UUID]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      uuid   = UUID.randomUUID()
    } yield PreviewResults.one(
      Preview(uuid).onRun(tools.setClipboard(uuid.toString)).score(Scores.high(input.context))
    )
}

object UUIDCommand extends CommandPlugin[UUIDCommand] {
  def make(config: Config): TaskManaged[UUIDCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield UUIDCommand(commandNames.getOrElse(List("uuid", "guuid")))
    )
}

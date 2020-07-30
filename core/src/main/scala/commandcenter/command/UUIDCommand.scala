package commandcenter.command

import java.util.UUID

import commandcenter.CCRuntime.Env
import commandcenter.util.ProcessUtil
import io.circe.Decoder
import zio.ZIO

final case class UUIDCommand() extends Command[UUID] {
  val commandType: CommandType = CommandType.UUIDCommand

  val commandNames: List[String] = List("uuid")

  val title: String = "UUID"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[UUID]]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      uuid  = UUID.randomUUID()
    } yield {
      List(Preview(uuid).onRun(ProcessUtil.copyToClipboard(uuid.toString)).score(Scores.high(input.context)))
    }
}

object UUIDCommand extends CommandPlugin[UUIDCommand] {
  implicit val decoder: Decoder[UUIDCommand] = Decoder.const(UUIDCommand())
}

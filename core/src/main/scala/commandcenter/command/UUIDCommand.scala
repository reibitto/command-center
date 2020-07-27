package commandcenter.command

import java.util.UUID

import commandcenter.CommandContext
import commandcenter.util.ProcessUtil
import io.circe.Decoder
import zio.{ IO, UIO }

final case class UUIDCommand() extends Command[UUID] {
  val commandType: CommandType = CommandType.UUIDCommand

  val commandNames: List[String] = List("uuid")

  val title: String = "UUID"

  override def keywordPreview(keyword: String, context: CommandContext): IO[CommandError, List[PreviewResult[UUID]]] = {
    val uuid = UUID.randomUUID()

    UIO(List(Preview(uuid).onRun(ProcessUtil.copyToClipboard(uuid.toString)).score(Scores.high(context))))
  }
}

object UUIDCommand extends CommandPlugin[UUIDCommand] {
  implicit val decoder: Decoder[UUIDCommand] = Decoder.const(UUIDCommand())
}

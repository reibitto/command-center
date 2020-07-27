package commandcenter.command

import commandcenter.CommandContext
import commandcenter.util.ProcessUtil
import io.circe.Decoder
import zio.blocking.Blocking
import zio.{ UIO, ZIO }

final case class EpochUnixCommand() extends Command[Long] {
  val commandType: CommandType = CommandType.EpochUnixCommand

  val commandNames: List[String] = List("epochunix")

  val title: String = "Epoch (Unix time)"

  override def keywordPreview(
    keyword: String,
    context: CommandContext
  ): ZIO[Blocking, CommandError, List[PreviewResult[Long]]] =
    for {
      epochTime <- UIO(System.currentTimeMillis() / 1000)
    } yield {
      List(
        Preview(epochTime)
          .score(Scores.high(context))
          .onRun(ProcessUtil.copyToClipboard(epochTime.toString))
      )
    }
}

object EpochUnixCommand extends CommandPlugin[EpochUnixCommand] {
  implicit val decoder: Decoder[EpochUnixCommand] = Decoder.const(EpochUnixCommand())
}

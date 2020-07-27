package commandcenter.command

import commandcenter.CommandContext
import commandcenter.util.ProcessUtil
import io.circe.Decoder
import zio.blocking.Blocking
import zio.{ UIO, ZIO }

final case class EpochMillisCommand() extends Command[Long] {
  val commandType: CommandType = CommandType.EpochMillisCommand

  val commandNames: List[String] = List("epochmillis")

  val title: String = "Epoch (milliseconds)"

  override def keywordPreview(
    keyword: String,
    context: CommandContext
  ): ZIO[Blocking, CommandError, List[PreviewResult[Long]]] =
    for {
      epochTime <- UIO(System.currentTimeMillis())
    } yield {
      List(
        Preview(epochTime)
          .score(Scores.high(context))
          .onRun(ProcessUtil.copyToClipboard(epochTime.toString))
      )
    }
}

object EpochMillisCommand extends CommandPlugin[EpochMillisCommand] {
  implicit val decoder: Decoder[EpochMillisCommand] = Decoder.const(EpochMillisCommand())
}

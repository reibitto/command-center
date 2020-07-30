package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.util.ProcessUtil
import io.circe.Decoder
import zio.{ UIO, ZIO }

final case class EpochUnixCommand() extends Command[Long] {
  val commandType: CommandType = CommandType.EpochUnixCommand

  val commandNames: List[String] = List("epochunix")

  val title: String = "Epoch (Unix time)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Long]]] =
    for {
      input     <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      epochTime <- UIO(System.currentTimeMillis() / 1000)
    } yield {
      List(
        Preview(epochTime)
          .score(Scores.high(input.context))
          .onRun(ProcessUtil.copyToClipboard(epochTime.toString))
      )
    }
}

object EpochUnixCommand extends CommandPlugin[EpochUnixCommand] {
  implicit val decoder: Decoder[EpochUnixCommand] = Decoder.const(EpochUnixCommand())
}

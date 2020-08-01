package commandcenter.command

import java.util.concurrent.TimeUnit

import commandcenter.CCRuntime.Env
import io.circe.Decoder
import zio.{ clock, ZIO }

final case class EpochUnixCommand() extends Command[Long] {
  val commandType: CommandType = CommandType.EpochUnixCommand

  val commandNames: List[String] = List("epochunix")

  val title: String = "Epoch (Unix time)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Long]]] =
    for {
      input     <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      epochTime <- clock.currentTime(TimeUnit.SECONDS)
    } yield {
      List(
        Preview(epochTime)
          .score(Scores.high(input.context))
          .onRun(input.context.ccProcess.setClipboard(epochTime.toString))
      )
    }
}

object EpochUnixCommand extends CommandPlugin[EpochUnixCommand] {
  implicit val decoder: Decoder[EpochUnixCommand] = Decoder.const(EpochUnixCommand())
}

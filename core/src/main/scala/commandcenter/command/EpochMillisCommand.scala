package commandcenter.command

import java.util.concurrent.TimeUnit

import commandcenter.CCRuntime.Env
import commandcenter.tools
import io.circe.Decoder
import zio.{ clock, ZIO }

final case class EpochMillisCommand() extends Command[Long] {
  val commandType: CommandType = CommandType.EpochMillisCommand

  val commandNames: List[String] = List("epochmillis")

  val title: String = "Epoch (milliseconds)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Long]]] =
    for {
      input     <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      epochTime <- clock.currentTime(TimeUnit.MILLISECONDS)
    } yield List(
      Preview(epochTime)
        .score(Scores.high(input.context))
        .onRun(tools.setClipboard(epochTime.toString))
    )
}

object EpochMillisCommand extends CommandPlugin[EpochMillisCommand] {
  implicit val decoder: Decoder[EpochMillisCommand] = Decoder.const(EpochMillisCommand())
}

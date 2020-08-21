package commandcenter.command

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools
import zio.{ clock, TaskManaged, ZIO, ZManaged }

final case class EpochMillisCommand(commandNames: List[String]) extends Command[Long] {
  val commandType: CommandType = CommandType.EpochMillisCommand
  val title: String            = "Epoch (milliseconds)"

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
  def make(config: Config): TaskManaged[EpochMillisCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield EpochMillisCommand(commandNames.getOrElse(List("epochmillis")))
    )
}

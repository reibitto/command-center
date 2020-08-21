package commandcenter.command

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools
import zio.{ clock, TaskManaged, ZIO, ZManaged }

final case class EpochUnixCommand(commandNames: List[String]) extends Command[Long] {
  val commandType: CommandType = CommandType.EpochUnixCommand
  val title: String            = "Epoch (Unix time)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Long]]] =
    for {
      input     <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      epochTime <- clock.currentTime(TimeUnit.SECONDS)
    } yield List(
      Preview(epochTime)
        .score(Scores.high(input.context))
        .onRun(tools.setClipboard(epochTime.toString))
    )
}

object EpochUnixCommand extends CommandPlugin[EpochUnixCommand] {
  def make(config: Config): TaskManaged[EpochUnixCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield EpochUnixCommand(commandNames.getOrElse(List("epochunix")))
    )
}

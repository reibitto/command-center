package commandcenter.command

import commandcenter.CommandContext
import commandcenter.util.OS
import io.circe.Decoder
import zio.process.{ Command => PCommand }
import zio.{ IO, UIO }

final case class LockCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.LockCommand

  // TODO: Sleep vs lock distinction?
  // /System/Library/CoreServices/Menu\ Extras/User.menu/Contents/Resources/CGSession -suspend
  val commandNames: List[String] = List("lock")

  val title: String = "Lock Computer"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  override def keywordPreview(
    keyword: String,
    context: CommandContext
  ): IO[CommandError, List[PreviewResult[Unit]]] =
    UIO(List(Preview.unit.onRun(PCommand("pmset", "displaysleepnow").exitCode.unit).score(Scores.high(context))))
}

object LockCommand extends CommandPlugin[LockCommand] {
  implicit val decoder: Decoder[LockCommand] = Decoder.const(LockCommand())
}

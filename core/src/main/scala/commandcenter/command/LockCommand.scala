package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.util.OS
import io.circe.Decoder
import zio.ZIO
import zio.process.{ Command => PCommand }

final case class LockCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.LockCommand

  // TODO: Sleep vs lock distinction?
  // /System/Library/CoreServices/Menu\ Extras/User.menu/Contents/Resources/CGSession -suspend
  val commandNames: List[String] = List("lock")

  val title: String = "Lock Computer"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield List(
      Preview.unit.onRun(PCommand("pmset", "displaysleepnow").exitCode.unit).score(Scores.high(input.context))
    )
}

object LockCommand extends CommandPlugin[LockCommand] {
  implicit val decoder: Decoder[LockCommand] = Decoder.const(LockCommand())
}

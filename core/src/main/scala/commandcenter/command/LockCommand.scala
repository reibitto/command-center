package commandcenter.command

import com.typesafe.config.Config
import commandcenter.util.OS
import commandcenter.CCRuntime.Env
import zio.*
import zio.process.Command as PCommand

// TODO: Sleep vs lock distinction?
// /System/Library/CoreServices/Menu\ Extras/User.menu/Contents/Resources/CGSession -suspend
final case class LockCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.LockCommand
  val title: String = "Lock Computer"

  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield PreviewResults.one(
      Preview.unit.onRun(pCommand.exitCode.unit).score(Scores.veryHigh(input.context))
    )

  private def pCommand: PCommand =
    OS.os match {
      case OS.MacOS => PCommand("pmset", "displaysleepnow")
      case _        => PCommand("rundll32", "user32.dll,LockWorkStation")
    }
}

object LockCommand extends CommandPlugin[LockCommand] {

  def make(config: Config): IO[CommandPluginError, LockCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield LockCommand(commandNames.getOrElse(List("lock")))
}

package commandcenter.command

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.{LPARAM, WPARAM}
import com.typesafe.config.Config
import commandcenter.util.OS
import commandcenter.CCRuntime.Env
import zio.*

final case class MuteCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.MuteCommand
  val title: String = "Toggle Mute"

  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    searchInput.keyword.map { input =>
      PreviewResults.one(
        Preview.unit.onRun(muteTask).score(Scores.veryHigh(input.context))
      )
    }

  private def muteTask: Task[Unit] =
    OS.os match {
      case OS.Windows =>
        ZIO.attemptBlocking {
          val APPCOMMAND_VOLUME_MUTE = 0x80000
//          val APPCOMMAND_VOLUME_UP = 0xA0000
//          val APPCOMMAND_VOLUME_DOWN = 0x90000
          val WM_APPCOMMAND = 0x319

          User32.INSTANCE.SendMessage(
            User32.INSTANCE.GetForegroundWindow(),
            WM_APPCOMMAND,
            new WPARAM(0),
            new LPARAM(APPCOMMAND_VOLUME_MUTE)
          )
        }
      case _ => ZIO.unit
    }
}

object MuteCommand extends CommandPlugin[MuteCommand] {

  def make(config: Config): IO[CommandPluginError, MuteCommand] =
    commandNames(config, "mute").map(MuteCommand(_))
}

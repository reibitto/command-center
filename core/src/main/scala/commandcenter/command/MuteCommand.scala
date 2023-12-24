package commandcenter.command

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.{DWORD, LPARAM, WORD, WPARAM}
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.KEYBDINPUT
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.util.OS
import zio.*

final case class MuteCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.MuteCommand
  val title: String = "Toggle Mute"

  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield PreviewResults.one(
      Preview.unit.onRun(muteTask).score(Scores.veryHigh(input.context))
    )

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
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield MuteCommand(commandNames.getOrElse(List("mute")))
}

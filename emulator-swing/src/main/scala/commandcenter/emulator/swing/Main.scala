package commandcenter.emulator.swing

import commandcenter.{CCApp, CCConfig, GlobalActions, TerminalType}
import commandcenter.emulator.swing.shortcuts.ShortcutsLive
import commandcenter.emulator.swing.ui.SwingTerminal
import commandcenter.shortcuts.Shortcuts
import commandcenter.CCRuntime.Env
import zio.*

object Main extends CCApp {
  val terminalType: TerminalType = TerminalType.Swing
  val shortcutsLayer: ULayer[Shortcuts] = ShortcutsLive.layer(this).!

  def run(args: List[String]): URIO[Env, ExitCode] =
    (for {
      config   <- CCConfig.load
      terminal <- SwingTerminal.create(this)
      _ <- (for {
             _ <- Shortcuts.addGlobalShortcut(config.keyboard.openShortcut)(_ =>
                    (for {
                      _ <- ZIO.logDebug("Opening emulated terminal...")
                      _ <- terminal.open
                      _ <- terminal.activate
                    } yield ()).ignore
                  )
             _ <- ZIO.logInfo(
                    s"Ready to accept input. Press `${config.keyboard.openShortcut}` to open the terminal."
                  )
             _ <- GlobalActions.setupCommon(config.globalActions)
           } yield ()).toManaged
    } yield terminal).use { terminal =>
      terminal.closePromise.await
    }.exitCode
}

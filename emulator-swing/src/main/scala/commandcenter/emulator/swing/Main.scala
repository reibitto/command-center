package commandcenter.emulator.swing

import commandcenter.{CCApp, CCConfig, GlobalActions, TerminalType}
import commandcenter.emulator.swing.shortcuts.ShortcutsLive
import commandcenter.emulator.swing.ui.SwingTerminal
import commandcenter.shortcuts.Shortcuts
import commandcenter.CCRuntime.Env
import zio.*
import zio.logging.log

object Main extends CCApp {
  val terminalType: TerminalType = TerminalType.Swing
  val shortcutsLayer: ULayer[Has[Shortcuts]] = ShortcutsLive.layer(this).!

  def run(args: List[String]): URIO[Env, ExitCode] =
    (for {
      config   <- CCConfig.load
      terminal <- SwingTerminal.create(this)
      _ <- (for {
             _ <- Shortcuts.addGlobalShortcut(config.keyboard.openShortcut)(_ =>
                    (for {
                      _ <- log.debug("Opening emulated terminal...")
                      _ <- terminal.open
                      _ <- terminal.activate
                    } yield ()).ignore
                  )
             _ <- log.info(
                    s"Ready to accept input. Press `${config.keyboard.openShortcut}` to open the terminal."
                  )
             _ <- GlobalActions.setupCommon(config.globalActions)
           } yield ()).toManaged_
    } yield terminal).use { terminal =>
      terminal.closePromise.await
    }.exitCode
}

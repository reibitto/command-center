package commandcenter.daemon

import commandcenter.CCRuntime.Env
import commandcenter.daemon.shortcuts.LiveShortcuts
import commandcenter.daemon.ui.SwingTerminal
import commandcenter.shortcuts.Shortcuts
import commandcenter.{ shortcuts, CCApp, CCConfig, TerminalType }
import zio._
import zio.logging.log

object Main extends CCApp {
  val terminalType: TerminalType        = TerminalType.Swing
  val shortcutsLayer: ULayer[Shortcuts] = LiveShortcuts.layer.orDie

  def run(args: List[String]): URIO[Env, ExitCode] =
    (for {
      config   <- CCConfig.load
      terminal <- SwingTerminal.create(config, this)
      _        <- (for {
                      _ <- shortcuts.addGlobalShortcut(config.keyboard.openShortcut)(_ =>
                             (for {
                               _ <- log.debug("Opening emulated terminal...")
                               _ <- terminal.open
                               _ <- terminal.activate
                             } yield ()).ignore
                           )
                      _ <- log.debug("Ready to accept input")
                    } yield ()).toManaged_
    } yield ()).useForever.exitCode
}

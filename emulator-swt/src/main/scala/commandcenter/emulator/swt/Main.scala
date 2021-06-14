package commandcenter.emulator.swt

import commandcenter.emulator.swt.shortcuts.ShortcutsLive
import commandcenter.emulator.swt.ui.{ RawSwtTerminal, SwtTerminal }
import commandcenter.shortcuts.Shortcuts
import commandcenter.{ CCRuntime, Conf, GlobalActions, TerminalType }
import zio._
import zio.logging.log

object Main {
  def main(args: Array[String]): Unit = {
    val runtime = new CCRuntime {
      def terminalType: TerminalType             = TerminalType.Swt
      def shortcutsLayer: ULayer[Has[Shortcuts]] = ShortcutsLive.layer(this).orDie
    }

    val config      = runtime.unsafeRun(Conf.config)
    val rawTerminal = new RawSwtTerminal(config)

    runtime.unsafeRunAsync_ {
      (for {
        terminal <- SwtTerminal.create(runtime, rawTerminal)
        _        <- (for {
                      _ <- Shortcuts.addGlobalShortcut(config.keyboard.openShortcut)(_ =>
                             (for {
                               _ <- log.debug("Opening emulated terminal...")
                               _ <- terminal.open
                               _ <- terminal.activate
                             } yield ()).ignore
                           )
                      _ <- log.debug("Ready to accept input")
                      _ <- GlobalActions.setupCommon(config.globalActions)
                    } yield ()).toManaged_
      } yield ()).useForever.exitCode
    }

    rawTerminal.loop()
  }
}

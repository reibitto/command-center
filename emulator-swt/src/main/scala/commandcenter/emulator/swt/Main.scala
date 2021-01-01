package commandcenter.emulator.swt

import commandcenter.emulator.swt.shortcuts.LiveShortcuts
import commandcenter.emulator.swt.ui.{ RawSwtTerminal, SwtTerminal }
import commandcenter.shortcuts.Shortcuts
import commandcenter.{ shortcuts, CCConfig, CCRuntime, TerminalType }
import zio._
import zio.logging.log

object Main {
  def main(args: Array[String]): Unit = {
    val runtime = new CCRuntime {
      def terminalType: TerminalType        = TerminalType.Swt
      def shortcutsLayer: ULayer[Shortcuts] = LiveShortcuts.layer(this).orDie
    }

    val config      = runtime.unsafeRun(CCConfig.load.useNow)
    val rawTerminal = new RawSwtTerminal(config)

    runtime.unsafeRunAsync_ {
      (for {
        terminal <- SwtTerminal.create(config, runtime, rawTerminal)
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

    rawTerminal.loop()
  }
}

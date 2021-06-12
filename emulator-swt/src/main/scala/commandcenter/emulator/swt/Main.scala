package commandcenter.emulator.swt

import commandcenter.emulator.swt.shortcuts.ShortcutsLive
import commandcenter.emulator.swt.ui.{ RawSwtTerminal, SwtTerminal }
import commandcenter.shortcuts.Shortcuts
import commandcenter.{ CCConfig, CCRuntime, GlobalActions, TerminalType }
import zio._
import zio.logging.log

import scala.util.Try

object Main {
  def main(args: Array[String]): Unit = {
    val runtime = new CCRuntime {
      def terminalType: TerminalType             = TerminalType.Swt
      def shortcutsLayer: ULayer[Has[Shortcuts]] = ShortcutsLive.layer(this).orDie
    }

    // The way this is set up is not typical. The reason for the weirdness is due to SWT needing to run with
    // `-XstartOnFirstThread` on macOS.
    val configReservation = runtime.unsafeRun(CCConfig.load.reserve)
    val config            = runtime.unsafeRun(configReservation.acquire)
    val rawTerminal       = new RawSwtTerminal(config)

    runtime.unsafeRunAsync_ {
      (for {
        terminal <- SwtTerminal.create(config, runtime, rawTerminal)
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

    val loopDone = Try(rawTerminal.loop())

    runtime.unsafeRun(configReservation.release(Exit.fromTry(loopDone)))
  }
}

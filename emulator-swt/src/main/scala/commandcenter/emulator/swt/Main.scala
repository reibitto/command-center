package commandcenter.emulator.swt

import commandcenter.*
import commandcenter.emulator.swt.shortcuts.ShortcutsLive
import commandcenter.emulator.swt.ui.{RawSwtTerminal, SwtTerminal}
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.ToolsLive
import commandcenter.CCRuntime.Env
import zio.*

object Main {
  type Environment = Scope & Env

  def main(args: Array[String]): Unit = {
    val runtime: Runtime.Scoped[Environment] = Unsafe.unsafe { implicit u =>
      Runtime.unsafe.fromLayer(
        ZLayer.make[Environment](
          ShortcutsLive.layer,
          ConfigLive.layer,
          ToolsLive.make,
          SttpLive.make,
          Runtime.removeDefaultLoggers >>> CCLogging.addLoggerFor(TerminalType.Swt),
          Scope.default
        )
      )
    }

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run {
        (for {
          runtime <- ZIO.runtime[Env]
          config  <- Conf.load
          rawTerminal = new RawSwtTerminal(config)
          terminal <- SwtTerminal.create(runtime, rawTerminal)
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
          // Written this way because SWT's UI loop must run from the main thread
          _ = rawTerminal.loop()
        } yield ()).tapErrorCause { c =>
          ZIO.logFatalCause("Fatal error", c)
        }
      }
    }
  }
}

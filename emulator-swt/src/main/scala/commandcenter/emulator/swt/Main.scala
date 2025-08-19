package commandcenter.emulator.swt

import commandcenter.*
import commandcenter.emulator.swt.shortcuts.ShortcutsLive
import commandcenter.emulator.swt.ui.{RawSwtTerminal, SwtTerminal}
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.ToolsLive
import commandcenter.util.EnvironmentSetup
import commandcenter.CCRuntime.Env
import zio.*

object Main {
  type Environment = Scope & Env

  def main(args: Array[String]): Unit = {
    EnvironmentSetup.setup()

    val runtime: Runtime.Scoped[Environment] = Unsafe.unsafe { implicit u =>
      Runtime.unsafe.fromLayer(
        Runtime.setExecutor(Executor.makeDefault(autoBlocking = false)) >>>
          Runtime.removeDefaultLoggers >>>
          ZLayer.make[Environment](
            ShortcutsLive.layer,
            ConfigLive.layer,
            ToolsLive.make,
            SttpLive.make,
            CCLogging.addLoggerFor(TerminalType.Swt),
            Runtime.setUnhandledErrorLogLevel(LogLevel.Warning),
            Scope.default
          )
      )
    }

    Unsafe.unsafe { implicit u =>
      // Written this way because SWT's UI loop must run from the main thread. That's why we do multiple, separate
      // unsafe run calls. Otherwise the next `flatMap` call might run in a ZScheduler worker thread.
      val config = runtime.unsafe.run(Conf.load).getOrThrowFiberFailure()

      val rawTerminal = runtime.unsafe.run(ZIO.succeed(new RawSwtTerminal(config))).getOrThrowFiberFailure()

      runtime.unsafe.run {
        (for {
          runtime  <- ZIO.runtime[Env]
          terminal <- SwtTerminal.create(runtime, rawTerminal)
          _        <- Shortcuts.addGlobalShortcut(config.keyboard.openShortcut)(_ =>
                 (for {
                   _ <- ZIO.logDebug("Opening emulated terminal...")
                   _ <- terminal.openActivated
                 } yield ()).ignore
               )
          _ <- ZIO.logInfo(
                 s"Ready to accept input. Press `${config.keyboard.openShortcut}` to open the terminal."
               )
          _ <- GlobalActions.setupCommon(config.globalActions)
        } yield ()).tapErrorCause { c =>
          ZIO.logFatalCause("Fatal error", c)
        }
      }

      runtime.unsafe.run(ZIO.succeed(rawTerminal.loop()))
    }
  }
}

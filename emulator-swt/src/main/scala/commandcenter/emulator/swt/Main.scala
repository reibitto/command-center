package commandcenter.emulator.swt

import commandcenter.*
import commandcenter.emulator.shortcuts.ShortcutsLive
import commandcenter.emulator.swt.ui.{RawSwtTerminal, SwtTerminal}
import commandcenter.util.EnvironmentSetup
import commandcenter.CCRuntime.Env
import zio.*

object Main {

  def main(args: Array[String]): Unit = {
    EnvironmentSetup.setup()

    // SWT's UI loop must run on the main thread, so the UI setup and loop are plain calls between separate
    // unsafe runs. Otherwise the next `flatMap` call might run in a ZScheduler worker thread.
    Unsafe.unsafe { implicit u =>
      val runtime = Runtime.unsafe.fromLayer(
        CCRuntime.layer(TerminalType.Swt, ShortcutsLive.layer(useSwingEventQueue = false)) ++ Scope.default
      )

      val config = runtime.unsafe.run(Conf.load).getOrThrowFiberFailure()

      val rawTerminal = new RawSwtTerminal(config)

      runtime.unsafe.run {
        (for {
          runtime  <- ZIO.runtime[Env]
          terminal <- SwtTerminal.create(runtime, rawTerminal)
          _        <- GlobalActions.setup(config)(terminal.openActivated)
        } yield ()).tapErrorCause(ZIO.logFatalCause("Fatal error", _))
      }

      rawTerminal.loop()
    }
  }
}

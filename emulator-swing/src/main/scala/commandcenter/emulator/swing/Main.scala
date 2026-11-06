package commandcenter.emulator.swing

import commandcenter.*
import commandcenter.emulator.shortcuts.ShortcutsLive
import commandcenter.emulator.swing.ui.SwingTerminal
import commandcenter.util.EnvironmentSetup
import commandcenter.CCRuntime.Env
import zio.*

object Main extends ZIOApp {

  override type Environment = Env

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  override def bootstrap: ZLayer[Any, Any, Environment] =
    CCRuntime.layer(TerminalType.Swing, ShortcutsLive.layer(useSwingEventQueue = true))

  def run: ZIO[ZIOAppArgs & Scope & Environment, Any, Unit] = {
    EnvironmentSetup.setup()

    (for {
      config   <- Conf.load
      terminal <- SwingTerminal.create
      _        <- GlobalActions.setup(config)(terminal.openAndActivate(config.general.reopenDelay))
      _        <- terminal.closePromise.await
    } yield ()).tapErrorCause(ZIO.logFatalCause(_))
  }

}

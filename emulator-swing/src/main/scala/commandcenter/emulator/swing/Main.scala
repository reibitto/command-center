package commandcenter.emulator.swing

import commandcenter.*
import commandcenter.emulator.swing.shortcuts.ShortcutsLive
import commandcenter.emulator.swing.ui.SwingTerminal
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.ToolsLive
import commandcenter.CCRuntime.Env
import zio.*

object Main extends ZIOApp {

  override type Environment = Env

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  override def bootstrap: ZLayer[Scope, Any, Environment] = ZLayer.make[Environment](
    ConfigLive.layer,
    ShortcutsLive.layer,
    ToolsLive.make,
    SttpLive.make,
    Runtime.removeDefaultLoggers >>> CCLogging.addLoggerFor(TerminalType.Swing),
    Scope.default
  )

  def run: ZIO[ZIOAppArgs & Scope & Environment, Any, ExitCode] = {
    (for {
      runtime  <- ZIO.runtime[Env]
      terminal <- SwingTerminal.create(runtime)
      config   <- Conf.config
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
      _ <- terminal.closePromise.await
    } yield ()).tapErrorCause(c => ZIO.debug(c.prettyPrint)).exitCode
  }

}

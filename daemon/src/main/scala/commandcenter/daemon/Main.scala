package commandcenter.daemon

import commandcenter.CCRuntime.Env
import commandcenter.command.SuspendProcessCommand
import commandcenter.daemon.ui.SwingTerminal
import commandcenter.{ CCApp, CCConfig }
import javax.swing.{ KeyStroke => JKeyStroke }
import zio._
import zio.logging.log

object Main extends CCApp {
  def run(args: List[String]): URIO[Env, ExitCode] =
    (for {
      provider <- HotKeyProvider.make
      config   <- CCConfig.load.toManaged_
      terminal <- SwingTerminal.create(config, this)
      _        <- (for {
                      _ <- provider.registerHotKey(JKeyStroke.getKeyStroke(config.keyboard.openShortcut))(_ =>
                             (for {
                               _ <- log.debug("Opening emulated terminal...")
                               _ <- terminal.open
                               _ <- terminal.activate
                             } yield ()).ignore
                           )
                      _ <- ZIO.foreach(config.keyboard.suspendShortcut) { suspendShortcut =>
                             provider.registerHotKey(JKeyStroke.getKeyStroke(suspendShortcut))(_ =>
                               (for {
                                 _   <- log.debug("Toggling suspend for frontmost process...")
                                 pid <- SuspendProcessCommand.toggleSuspendFrontProcess
                                 _   <- log.debug(s"Toggled suspend for process $pid")
                               } yield ()).ignore
                             )
                           }
                      _ <- log.debug("Ready to accept input")
                    } yield ()).toManaged_
    } yield ()).useForever.exitCode
}

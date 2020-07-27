package commandcenter.daemon

import commandcenter.CCRuntime.Env
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
      _ <- (for {
            _ <- provider.registerHotKey(JKeyStroke.getKeyStroke("meta SPACE"))(_ =>
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

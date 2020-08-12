package commandcenter.cli

import com.googlecode.lanterna.input.{ KeyStroke, KeyType }
import commandcenter.CCRuntime.Env
import commandcenter.command._
import commandcenter.shortcuts.Shortcuts
import commandcenter.ui.{ CliTerminal, EventResult }
import commandcenter.{ CCApp, CCConfig }
import zio._
import zio.stream.ZStream

object Main extends CCApp {
  def run(args: List[String]): URIO[Env, ExitCode] =
    (for {
      config   <- CCConfig.load
      terminal <- CliTerminal.createNative(config)
      exitCode <- (for {
                      _ <- terminal.keyHandlersRef.set(
                             terminal.defaultKeyHandlers ++ Map(
                               new KeyStroke(KeyType.Escape) -> UIO(EventResult.Exit)
                             )
                           )
                      _ <- Task(terminal.screen.startScreen())
                      _ <- terminal.render(SearchResults.empty)
                      _ <- ZStream
                             .fromQueue(terminal.renderQueue)
                             .foreach(terminal.render)
                             .forkDaemon
                      _ <- terminal
                             .processEvent(config.commands, config.aliases)
                             .repeatWhile {
                               case EventResult.Exit               => false
                               case EventResult.UnexpectedError(t) =>
                                 // TODO: Log error
                                 true
                               case EventResult.Success            => true
                             }
                    } yield ()).exitCode.toManaged_
    } yield exitCode).useNow.catchAll { t =>
      t.printStackTrace()
      UIO(ExitCode.failure)
    }

  val shortcutsLayer: ULayer[Shortcuts] = Shortcuts.unsupported
}

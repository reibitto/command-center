package commandcenter.cli

import com.googlecode.lanterna.input.{ KeyStroke, KeyType }
import commandcenter.CCRuntime.Env
import commandcenter.command._
import commandcenter.ui.{ CliTerminal, EventResult }
import commandcenter.{ CCApp, CCConfig }
import zio._
import zio.stream.ZStream

object Main extends CCApp {
  def run(args: List[String]): URIO[Env, ExitCode] =
    for {
      config <- CCConfig.load.orDie
      exitCode <- CliTerminal
                   .createNative(config)
                   .use { terminal =>
                     for {
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
                             .doWhile {
                               case EventResult.Exit               => false
                               case EventResult.UnexpectedError(t) =>
                                 // TODO: Log error
                                 true
                               case EventResult.Success => true
                             }
                     } yield ()
                   }
                   .exitCode
    } yield exitCode

}

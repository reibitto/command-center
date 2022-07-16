package commandcenter.cli

import commandcenter.*
import commandcenter.command.*
import commandcenter.shortcuts.Shortcuts
import commandcenter.ui.{CliTerminal, EventResult}
import commandcenter.CCRuntime.Env
import zio.*
import zio.console.*
import zio.stream.ZStream

object Main extends CCApp {
  val terminalType: TerminalType = TerminalType.Cli
  val shortcutsLayer: ULayer[Has[Shortcuts]] = Shortcuts.unsupported

  def uiLoop: RManaged[Env, ExitCode] =
    for {
      terminal <- CliTerminal.createNative
      exitCode <- (for {
                    _ <- terminal.keyHandlersRef.set(terminal.defaultKeyHandlers)
                    _ <- Task(terminal.screen.startScreen())
                    _ <- terminal.render(SearchResults.empty)
                    _ <- ZStream
                           .fromQueue(terminal.renderQueue)
                           .foreach(terminal.render)
                           .forkDaemon
                    config <- Conf.config
                    _ <- terminal
                           .processEvent(config.commands, config.aliases)
                           .repeatWhile {
                             case EventResult.Exit               => false
                             case EventResult.UnexpectedError(t) =>
                               // TODO: Log error
                               true
                             case EventResult.Success | EventResult.RemainOpen => true
                           }
                  } yield ()).exitCode.toManaged_
    } yield exitCode

  def run(args: List[String]): URIO[Env, ExitCode] =
    CliArgs.rootCommand
      .parse(args)
      .fold(
        help => putStrLn(help.toString),
        {
          case CliCommand.Standalone =>
            uiLoop.useNow.tapError { t =>
              UIO(t.printStackTrace())
            }

          case CliCommand.Help =>
            putStrLn(CliArgs.rootCommand.showHelp)

          case CliCommand.Version => putStrLn(s"Command Center CLI v${commandcenter.BuildInfo.version}")
        }
      )
      .exitCode
}

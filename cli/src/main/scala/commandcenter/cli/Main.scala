package commandcenter.cli

import commandcenter.*
import commandcenter.command.*
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.ToolsLive
import commandcenter.ui.{CliTerminal, EventResult}
import commandcenter.CCRuntime.Env
import zio.*
import zio.stream.ZStream
import zio.Console.printLine

object Main extends ZIOApp {

  override type Environment = Env

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  override def bootstrap: ZLayer[Any, Any, Environment] = ZLayer.make[Environment](
    ConfigLive.layer,
    Shortcuts.unsupported,
    ToolsLive.make,
    SttpLive.make,
    Runtime.removeDefaultLoggers >>> CCLogging.addLoggerFor(TerminalType.Cli)
  )

  def uiLoop: RIO[Scope & Env, ExitCode] =
    for {
      terminal <- CliTerminal.createNative
      exitCode <- (for {
                    _ <- terminal.keyHandlersRef.set(terminal.defaultKeyHandlers)
                    _ <- ZIO.attempt(terminal.screen.startScreen())
                    _ <- terminal.render(SearchResults.empty)
                    _ <- ZStream
                           .fromQueue(terminal.renderQueue)
                           .foreach(terminal.render)
                           .forkDaemon
                    config <- Conf.load
                    _ <- terminal
                           .processEvent(config.commands, config.aliases)
                           .repeatWhile {
                             case EventResult.Exit               => false
                             case EventResult.UnexpectedError(t) =>
                               // TODO: Log error
                               true
                             case EventResult.Success | EventResult.RemainOpen => true
                           }
                  } yield ()).exitCode
    } yield exitCode

  def run: ZIO[ZIOAppArgs & Scope & Environment, Any, ExitCode] =
    for {
      args <- ZIOAppArgs.getArgs
      exitCode <- CliArgs.rootCommand
                    .parse(args)
                    .fold(
                      help => printLine(help.toString),
                      {
                        case CliCommand.Standalone =>
                          uiLoop.tapError { t =>
                            ZIO.succeed(t.printStackTrace())
                          }

                        case CliCommand.Help =>
                          printLine(CliArgs.rootCommand.showHelp)

                        case CliCommand.Version => printLine(s"Command Center CLI v${commandcenter.BuildInfo.version}")
                      }
                    )
                    .exitCode
    } yield exitCode

}

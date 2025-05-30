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
    Runtime.removeDefaultLoggers >>> CCLogging.addLoggerFor(TerminalType.Cli),
    Runtime.setUnhandledErrorLogLevel(LogLevel.Warning)
  )

  def uiLoop(config: CCConfig): RIO[Scope & Env, Unit] =
    for {
      terminal <- CliTerminal.createNative
      _        <- terminal.keyHandlersRef.set(terminal.defaultKeyHandlers)
      _        <- ZIO.attempt(terminal.screen.startScreen())
      _        <- terminal.render(SearchResults.empty)
      _        <- ZStream
             .fromQueue(terminal.renderQueue)
             .foreach(terminal.render)
             .forkDaemon
      _ <- terminal
             .processEvent(config.commands, config.aliases)
             .repeatWhile {
               case EventResult.Exit =>
                 false

               case EventResult.UnexpectedError(t) =>
                 // TODO: Log error. Either to file or in an error PreviewResult as to not ruin the CLI GUI layout.
                 true

               case EventResult.Success | EventResult.RemainOpen =>
                 true
             }
    } yield ()

  def run: ZIO[ZIOAppArgs & Scope & Environment, Any, ExitCode] =
    (for {
      args   <- ZIOAppArgs.getArgs
      config <- Conf.load
      _      <- CliArgs.rootCommand
             .parse(args)
             .fold(
               help => printLine(help.toString),
               {
                 case CliCommand.Standalone =>
                   uiLoop(config)

                 case CliCommand.Help =>
                   printLine(CliArgs.rootCommand.showHelp)

                 case CliCommand.Version =>
                   printLine(s"Command Center CLI v${commandcenter.BuildInfo.version}")
               }
             )
    } yield ExitCode.success).tapErrorCause { t =>
      ZIO.succeed(t.squash.printStackTrace()).exitCode
    }

}

package commandcenter.cli

import commandcenter.*
import commandcenter.command.*
import commandcenter.shortcuts.Shortcuts
import commandcenter.ui.{CliTerminal, EventResult}
import commandcenter.util.EnvironmentSetup
import commandcenter.CCRuntime.Env
import zio.*
import zio.stream.ZStream
import zio.Console.printLine

object Main extends ZIOApp {

  override type Environment = Env

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  override def bootstrap: ZLayer[Any, Any, Environment] =
    CCRuntime.layer(TerminalType.Cli, Shortcuts.unsupported)

  def uiLoop(config: CCConfig): RIO[Scope & Env, Unit] =
    for {
      terminal <- CliTerminal.createNative
      _        <- terminal.keyHandlersRef.set(terminal.defaultKeyHandlers)
      _        <- ZIO.attempt(terminal.screen.startScreen())
      _        <- terminal.render(SearchResults.empty)
      _        <- ZStream
             .fromQueue(terminal.renderQueue)
             // One failed render (including defects) must not stop all future renders
             .foreach(terminal.render(_).catchAllCause(ZIO.logErrorCause("Failed to render", _)))
             .forkScoped
      _ <- terminal
             .processEvent(config.commands, config.aliases)
             // TODO: Log `UnexpectedError`. Either to file or in an error PreviewResult as to not ruin the CLI GUI layout.
             .repeatWhile(_ != EventResult.Exit)
    } yield ()

  def run: ZIO[ZIOAppArgs & Scope & Environment, Any, Unit] = {
    EnvironmentSetup.setup()

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
    } yield ()).tapErrorCause { t =>
      ZIO.succeed(t.squash.printStackTrace())
    }
  }

}

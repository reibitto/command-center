package commandcenter.cli

import com.googlecode.lanterna.input.{ KeyStroke, KeyType }
import commandcenter.CCRuntime.Env
import commandcenter._
import commandcenter.cli.message.{ CCRequest, CCResponse }
import commandcenter.cli.zmq.{ ZPublisher, ZSubscriber }
import commandcenter.command._
import commandcenter.shortcuts.Shortcuts
import commandcenter.ui.{ CliTerminal, EventResult }
import io.circe.syntax._
import org.zeromq.{ SocketType, ZMQ }
import zio._
import zio.console._
import zio.logging.log
import zio.stream.ZStream

object Main extends CCApp {
  val terminalType: TerminalType        = TerminalType.Cli
  val shortcutsLayer: ULayer[Shortcuts] = Shortcuts.unsupported

  def uiLoop: RManaged[Env, ExitCode] =
    for {
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
    } yield exitCode

  def headlessLoop: RManaged[Env, Unit] =
    for {
      config    <- CCConfig.load
      terminal  <- HeadlessTerminal.create
      zmqContext = ZMQ.context(1)
      pub        = ZPublisher(zmqContext.socket(SocketType.PUB))
      sub        = ZSubscriber(zmqContext.socket(SocketType.SUB))
      subAddress = "tcp://*:9980" // TODO: Make ports configurable. Also accept `ipc://port` format and so on
      _         <- pub.bind("tcp://*:9981")
      _         <- sub.connect(subAddress)
      _         <- sub.subscribeAll.toManaged_
      _         <- putStrLn(s"Waiting for requests at $subAddress").toManaged_
      _         <-
        (for {
          rawMessage           <- sub.receiveString
          message               = io.circe.parser.parse(rawMessage).flatMap(_.as[CCRequest])
          (jsonResponse, cont) <- message match {
                                    case Right(CCRequest.Search(term, _)) =>
                                      for {
                                        results <- terminal.search(config.commands, config.aliases)(term)
                                        response = CCResponse.Search.fromSearchResults(results)
                                      } yield (response.asJson, true)

                                    case Right(CCRequest.Run(index, _)) =>
                                      for {
                                        result <- terminal.run(index)
                                      } yield (CCResponse.Run(result.isDefined).asJson, true)

                                    case Right(CCRequest.Exit(_)) => UIO((CCResponse.Exit.asJson, false))

                                    case Left(error) =>
                                      for {
                                        _ <- log.throwable("Received an invalid request", error)
                                      } yield (CCResponse.Error(s"Invalid request: ${error.getMessage}").asJson, true)
                                  }
          _                    <- pub.send(jsonResponse.noSpaces)
        } yield cont).repeatWhile(identity).toManaged_
    } yield ()

  def run(args: List[String]): URIO[Env, ExitCode] =
    CliArgs.rootCommand
      .parse(args)
      .fold(
        help => putStrLn(help.toString),
        {
          case CliCommand.Headless =>
            headlessLoop.useNow

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

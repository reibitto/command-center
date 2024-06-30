package commandcenter

import zio.*
import zio.logging.*
import zio.logging.LogFilter.LogLevelByNameConfig
import zio.logging.LogFormat.*

object CCLogging {

  val coloredFormat: LogFormat =
    timestamp.color(LogColor.BLUE) |-|
      level.highlight |-|
      fiberId.color(LogColor.WHITE) |-|
      line.highlight |-|
      newLine +
      cause.highlight.filter(LogFilter.causeNonEmpty)

  def addLoggerFor(terminalType: TerminalType): ZLayer[Any, Nothing, Unit] =
    terminalType match {
      case TerminalType.Cli => ZLayer.empty.unit // TODO: File logging

      case TerminalType.Swing | TerminalType.Swt =>
        val defaultLogLevel = LogLevel.Info

        ZLayer.scopedEnvironment(
          for {
            logLevelString <- System
                                .envOrElse("COMMAND_CENTER_LOG_LEVEL", defaultLogLevel.label)
                                .catchAllCause(t => ZIO.debug(t.prettyPrint).as(defaultLogLevel.label))
            logLevel <- ZIO.fromOption(LogLevel.levels.find(_.label.equalsIgnoreCase(logLevelString))).catchAll { _ =>
                          ZIO
                            .debug(s"Could not find log level: `$logLevelString`. Defaulting to $defaultLogLevel")
                            .as(defaultLogLevel)
                        }
            env <- consoleLogger(ConsoleLoggerConfig(coloredFormat, LogLevelByNameConfig(logLevel))).build
          } yield env
        )

      case TerminalType.Test => ZLayer.empty.unit
    }
}

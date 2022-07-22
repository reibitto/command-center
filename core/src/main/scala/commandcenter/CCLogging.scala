package commandcenter

import zio.{LogLevel, ZLayer}
import zio.logging.{console, LogColor, LogFormat}
import zio.logging.LogFormat.*

object CCLogging {

  val coloredFormat: LogFormat =
    timestamp.color(LogColor.BLUE) |-|
      level.highlight |-|
      fiberId.color(LogColor.WHITE) |-|
      line.highlight |-|
      newLine |-|
      cause.highlight

  def addLoggerFor(terminalType: TerminalType): ZLayer[Any, Nothing, Unit] =
    terminalType match {
      case TerminalType.Cli => ZLayer.empty.unit // TODO: File logging

      case TerminalType.Swing | TerminalType.Swt =>
        console(coloredFormat, LogLevel.Info)

      case TerminalType.Test => ZLayer.empty.unit
    }
}

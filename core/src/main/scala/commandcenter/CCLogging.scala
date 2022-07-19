package commandcenter

import zio.logging.{console, LogColor, LogFormat}
import zio.logging.LogFormat.{fiberId, level, line, timestamp}
import zio.ZLayer

object CCLogging {

  val coloredFormat: LogFormat =
    timestamp.color(LogColor.BLUE) |-|
      level.highlight |-|
      fiberId.color(LogColor.WHITE) |-|
      line.highlight

  def addLoggerFor(terminalType: TerminalType): ZLayer[Any, Nothing, Unit] =
    terminalType match {
      case TerminalType.Cli => ZLayer.empty.unit // TODO: File logging

      case TerminalType.Swing | TerminalType.Swt =>
        console(coloredFormat)

      case TerminalType.Test => ZLayer.empty.unit
    }
}

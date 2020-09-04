package commandcenter

import zio._
import zio.clock.Clock
import zio.console._
import zio.logging._

object CCLogging {
  def make(terminalType: TerminalType): ZLayer[Console with Clock, Nothing, Logging] =
    terminalType match {
      case TerminalType.Cli => Logging.ignore // TODO: File logging

      case TerminalType.Swing =>
        val format = LogFormat.ColoredLogFormat((_, s) => s)
        Logging.console(LogLevel.Trace, format)

      case TerminalType.Test  => Logging.ignore
    }
}

package commandcenter

import zio.*
import zio.clock.Clock
import zio.console.*
import zio.logging.*

object CCLogging {

  def make(terminalType: TerminalType): ZLayer[Console with Clock, Nothing, Logging] =
    terminalType match {
      case TerminalType.Cli => Logging.ignore // TODO: File logging

      case TerminalType.Swing | TerminalType.Swt =>
        val format = LogFormat.ColoredLogFormat((_, s) => s)
        Logging.console(LogLevel.Trace, format)

      case TerminalType.Test => Logging.ignore
    }
}

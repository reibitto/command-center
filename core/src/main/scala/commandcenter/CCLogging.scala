//package commandcenter
//
//import zio.*
//import zio.Clock
//import zio.Console
//import zio.Console.*
//
//object CCLogging {
//
//  def make(terminalType: TerminalType): ZLayer[Any, Nothing, Logging] =
//    terminalType match {
//      case TerminalType.Cli => Logging.ignore // TODO: File logging
//
//      case TerminalType.Swing | TerminalType.Swt =>
//        val format = LogFormat.ColoredLogFormat((_, s) => s)
//        Logging.console(LogLevel.Trace, format)
//
//      case TerminalType.Test => Logging.ignore
//    }
//}
// ???

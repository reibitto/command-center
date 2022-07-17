package commandcenter.emulator.swt

import commandcenter.{CCRuntime, Conf, ConfigLive, GlobalActions, TerminalType}
import commandcenter.emulator.swt.shortcuts.ShortcutsLive
import commandcenter.emulator.swt.ui.{RawSwtTerminal, SwtTerminal}
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.{Tools, ToolsLive}
import zio.*
import zio.managed.*

object Main extends ZIOApp {

  override type Environment = Conf & Tools & Shortcuts

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  override def bootstrap: ZLayer[Scope, Any, Environment] = ZLayer.make[Environment](
    ConfigLive.layer,
    ShortcutsLive.layer,
    ToolsLive.make,
    Scope.default
  )

  def run: ZIO[ZIOAppArgs & Scope & Environment, Any, ExitCode] = {
    (for {
      aliases <- Conf.get(_.aliases)
      _ <- Console.printLine(s"${aliases}")
      _ <- Console.printLine("hi")
    } yield ()).exitCode
  }


}

//object Main {
//
//  def main(args: Array[String]): Unit = {
////    val runtime = new CCRuntime {
////      def terminalType: TerminalType = TerminalType.Swt
////      def shortcutsLayer: ULayer[Shortcuts] = ShortcutsLive.layer(this).!
////    }
//
//    val runtime: CCRuntime = ???
//
//    val config = Unsafe.unsafe { implicit u =>
//      runtime.unsafe.run(Conf.config).getOrThrow()
//    }
//
//    val rawTerminal = new RawSwtTerminal(config)
//
//    Unsafe.unsafe { implicit u =>
//      runtime.unsafe.fork {
//        (for {
//          terminal <- SwtTerminal.create(runtime, rawTerminal)
//          _ <- (for {
//            _ <- Shortcuts.addGlobalShortcut(config.keyboard.openShortcut)(_ =>
//              (for {
//                _ <- ZIO.logDebug("Opening emulated terminal...")
//                _ <- terminal.open
//                _ <- terminal.activate
//              } yield ()).ignore
//            )
//            _ <- ZIO.logInfo(
//              s"Ready to accept input. Press `${config.keyboard.openShortcut}` to open the terminal."
//            )
//            _ <- GlobalActions.setupCommon(config.globalActions)
//          } yield ()).toManaged
//        } yield ()).useForever.exitCode
//      }
//
//    }
//
//    rawTerminal.loop()
//  }
//}

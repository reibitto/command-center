package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.{Runtime, ULayer}

import java.util.concurrent.Executor

trait CCRuntime extends Runtime[Env] {

  class DirectExecutor extends Executor {
    def execute(command: Runnable): Unit = command.run()
  }

  lazy val runtime: Runtime[Env] = {
    ???
//    val platform =
//      if (OS.os == OS.MacOS && terminalType == TerminalType.Swt)
//        Platform.fromExecutionContext(ExecutionContext.fromExecutor(new DirectExecutor()))
//      else
//        Platform.default
//
//    Runtime.unsafeFromLayer(
//      ZLayer.fromMagic[PartialEnv](
//        ZEnv.live,
////        CCLogging.make(terminalType),
//        ToolsLive.make.!,
//        shortcutsLayer,
//        HttpClientZioBackend.layer()
//      ) >+> ConfigLive.layer.!,
//      platform
//    )
  }

  def shortcutsLayer: ULayer[Shortcuts]
  def terminalType: TerminalType

//  lazy val environment: Env = runtime.environment
//  lazy val platform: Platform = runtime.platform
}

object CCRuntime {
  type PartialEnv = Tools & Shortcuts
  type Env = PartialEnv & Conf
}

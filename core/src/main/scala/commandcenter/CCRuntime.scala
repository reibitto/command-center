package commandcenter

import commandcenter.CCRuntime.{ Env, PartialEnv }
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.{ Tools, ToolsLive }
import commandcenter.util.OS
import sttp.client.httpclient.zio.{ HttpClientZioBackend, SttpClient }
import zio.internal.Platform
import zio.logging.Logging
import zio.{ Has, Runtime, ULayer, ZEnv, ZLayer }

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

trait CCRuntime extends Runtime[Env] {

  class DirectExecutor extends Executor {
    def execute(command: Runnable): Unit = command.run()
  }

  lazy val runtime: Runtime[Env] = {
    val platform =
      if (OS.os == OS.MacOS && terminalType == TerminalType.Swt)
        Platform.fromExecutionContext(ExecutionContext.fromExecutor(new DirectExecutor()))
      else
        Platform.default

    import zio.magic._

    Runtime.unsafeFromLayer(
      ZLayer.fromMagic[PartialEnv](
        ZEnv.live,
        CCLogging.make(terminalType),
        ToolsLive.make.orDie,
        shortcutsLayer,
        HttpClientZioBackend.layer()
      ) >+> ConfigLive.layer.orDie,
      platform
    )
  }

  def shortcutsLayer: ULayer[Has[Shortcuts]]
  def terminalType: TerminalType

  lazy val environment: Env   = runtime.environment
  lazy val platform: Platform = runtime.platform
}

object CCRuntime {
  type PartialEnv = ZEnv with Logging with SttpClient with Has[Tools] with Has[Shortcuts]
  type Env        = PartialEnv with Has[Conf]
}

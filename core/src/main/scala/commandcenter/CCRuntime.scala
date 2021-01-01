package commandcenter

import commandcenter.CCRuntime.Env
import commandcenter.command.cache.InMemoryCache
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools
import commandcenter.util.OS
import sttp.client.httpclient.zio.{ HttpClientZioBackend, SttpClient }
import zio.duration._
import zio.internal.Platform
import zio.logging.Logging
import zio.{ Runtime, ULayer, ZEnv }

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

trait CCRuntime extends Runtime[Env] {

  class DirectExecutor extends Executor {
    def execute(command: Runnable): Unit = command.run()
  }

  lazy val runtime: Runtime.Managed[Env] = {
    val platform =
      if (OS.os == OS.MacOS && terminalType == TerminalType.Swt)
        Platform.fromExecutionContext(ExecutionContext.fromExecutor(new DirectExecutor()))
      else
        Platform.default

    Runtime.unsafeFromLayer(
      ZEnv.live >>> (
        ZEnv.live
          ++ CCLogging.make(terminalType)
          ++ Tools.live
          ++ shortcutsLayer
          ++ HttpClientZioBackend.layer()
          ++ InMemoryCache.make(5.minutes)
      ),
      platform
    )
  }

  def shortcutsLayer: ULayer[Shortcuts]
  def terminalType: TerminalType

  lazy val environment: Env   = runtime.environment
  lazy val platform: Platform = runtime.platform
}

object CCRuntime {
  type Env = ZEnv with Logging with Tools with Shortcuts with SttpClient with InMemoryCache
}

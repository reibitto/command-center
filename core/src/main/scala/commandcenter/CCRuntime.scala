package commandcenter

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

import commandcenter.CCRuntime.Env
import commandcenter.command.cache.InMemoryCache
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools
import sttp.client.httpclient.zio.{ HttpClientZioBackend, SttpClient }
import zio.duration._
import zio.internal.Platform
import zio.logging.Logging
import zio.{ Runtime, ULayer, ZEnv }

trait CCRuntime extends Runtime[Env] {
  val executionContext = ExecutionContext.fromExecutor(new DirectExecutor())

  lazy val runtime: Runtime.Managed[Env] = Runtime.unsafeFromLayer(
    ZEnv.live >>> (
      ZEnv.live
        ++ CCLogging.make(terminalType)
        ++ Tools.live
        ++ shortcutsLayer
        ++ HttpClientZioBackend.layer()
        ++ InMemoryCache.make(5.minutes)
    ),
    Platform.fromExecutionContext(executionContext) // TODO:: Only if first thread is set, otherwise Platform.default
  )

  def shortcutsLayer: ULayer[Shortcuts]
  def terminalType: TerminalType

  lazy val environment: Env   = runtime.environment
  lazy val platform: Platform = runtime.platform
}

object CCRuntime {
  type Env = ZEnv with Logging with Tools with Shortcuts with SttpClient with InMemoryCache
}

class DirectExecutor extends Executor {
  def execute(command: Runnable): Unit = command.run()
}

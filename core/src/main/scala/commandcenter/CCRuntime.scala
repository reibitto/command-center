package commandcenter

import commandcenter.CCRuntime.Env
import commandcenter.command.cache.InMemoryCache
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools
import sttp.client.httpclient.zio.{ HttpClientZioBackend, SttpClient }
import zio.duration._
import zio.internal.Platform
import zio.logging.{ LogLevel, Logging }
import zio.{ Runtime, ULayer, ZEnv }

trait CCRuntime extends Runtime[Env] {
  lazy val runtime: Runtime.Managed[Env] = Runtime.unsafeFromLayer {
    ZEnv.live >>> (
      ZEnv.live
        ++ Logging.console(LogLevel.Trace)
        ++ Tools.live
        ++ shortcutsLayer
        ++ HttpClientZioBackend.layer()
        ++ InMemoryCache.make(5.minutes)
    )
  }

  def shortcutsLayer: ULayer[Shortcuts]

  lazy val environment: Env   = runtime.environment
  lazy val platform: Platform = runtime.platform
}

object CCRuntime {
  type Env = ZEnv with Logging with Tools with Shortcuts with SttpClient with InMemoryCache
}

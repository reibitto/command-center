package commandcenter

import commandcenter.CCRuntime.Env
import commandcenter.tools.Tools
import sttp.client.httpclient.zio.{ HttpClientZioBackend, SttpClient }
import zio.internal.Platform
import zio.logging.Logging
import zio.{ Runtime, ZEnv }

trait CCRuntime extends Runtime[Env] {
  lazy val runtime: Runtime.Managed[Env] = Runtime.unsafeFromLayer {
    ZEnv.live >>> (
      ZEnv.live
        ++ Logging.console((_, logEntry) => logEntry)
        ++ Tools.live
        ++ HttpClientZioBackend.layer()
    )
  }

  lazy val environment: Env   = runtime.environment
  lazy val platform: Platform = runtime.platform
}

object CCRuntime {
  type Env = ZEnv with Logging with Tools with SttpClient

  lazy val default: CCRuntime = new CCRuntime {}
}

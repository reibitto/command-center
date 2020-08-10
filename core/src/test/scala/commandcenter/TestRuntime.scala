package commandcenter

import commandcenter.tools.Tools
import sttp.client.httpclient.zio.SttpClient
import zio.ZEnv
import zio.logging.Logging
import zio.test.environment._
import zio.test.{ Annotations, Sized }

object TestRuntime {
  type TestEnv = ZEnv
    with Annotations
    with TestClock
    with TestConsole
    with Live
    with TestRandom
    with Sized
    with TestSystem
    with Logging
    with Tools
    with SttpClient
}

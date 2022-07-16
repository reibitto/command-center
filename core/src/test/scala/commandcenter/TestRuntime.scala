package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools
import sttp.client3.httpclient.zio.SttpClient
import zio.{Has, ZEnv}
import zio.logging.Logging
import zio.test.{Annotations, Sized}
import zio.test.environment.*

object TestRuntime {

  type TestPartialEnv = ZEnv
    with Annotations
    with TestClock
    with TestConsole
    with Live
    with TestRandom
    with Sized
    with TestSystem
    with Logging
    with SttpClient
    with Has[Tools]
    with Has[Shortcuts]

  type TestEnv = TestPartialEnv with Has[Conf]
}

package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools
import sttp.client3.httpclient.zio.SttpClient
import zio.test.{Annotations, Live, Sized, TestClock, TestConsole, TestRandom, TestSystem}
import zio.ZEnv

object TestRuntime {

  type TestPartialEnv = ZEnv
    with Annotations
    with TestClock
    with TestConsole
    with Live
    with TestRandom
    with Sized
    with TestSystem
    with SttpClient
    with Tools
    with Shortcuts

  type TestEnv = TestPartialEnv with Conf
}

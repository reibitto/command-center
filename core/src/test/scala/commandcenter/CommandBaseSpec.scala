package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.ToolsLive
import commandcenter.TestRuntime.{TestEnv, TestPartialEnv}
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.{Layer, ZLayer}
import zio.test.{RunnableSpec, TestAspect, TestExecutor, TestRunner}
import zio.test.environment.testEnvironment

import java.util.Locale

trait CommandBaseSpec extends RunnableSpec[TestEnv, Any] {

  val testEnv: Layer[Throwable, TestEnv] = {
    ZLayer.fromMagic[TestPartialEnv](
      testEnvironment,
      CCLogging.make(TerminalType.Test),
      ToolsLive.make.!,
      Shortcuts.unsupported,
      HttpClientZioBackend.layer()
    ) ++ ConfigFake.layer
  }

  val defaultCommandContext: CommandContext =
    CommandContext(Locale.ENGLISH, TestTerminal, 1.0)

  override def aspects: List[TestAspect[Nothing, TestEnv, Nothing, Any]] =
    List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[TestEnv, Any] =
    TestRunner(TestExecutor.default(testEnv.!))
}

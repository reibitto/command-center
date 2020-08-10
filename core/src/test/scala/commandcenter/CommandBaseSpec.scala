package commandcenter

import java.util.Locale

import commandcenter.TestRuntime.TestEnv
import commandcenter.tools.Tools
import sttp.client.httpclient.zio.HttpClientZioBackend
import zio.Layer
import zio.duration._
import zio.logging.Logging
import zio.test.environment.testEnvironment
import zio.test.{ RunnableSpec, TestAspect, TestExecutor, TestRunner }

trait CommandBaseSpec extends RunnableSpec[TestEnv, Any] {
  val testEnv: Layer[Throwable, TestEnv] =
    testEnvironment >>> (
      testEnvironment
        ++ Logging.console((_, logEntry) => logEntry)
        ++ Tools.live
        ++ HttpClientZioBackend.layer()
    )

  val defaultCommandContext: CommandContext =
    CommandContext(Locale.ENGLISH, TestTerminal, 1.0)

  override def aspects: List[TestAspect[Nothing, TestEnv, Nothing, Any]] =
    List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[TestEnv, Any] =
    TestRunner(TestExecutor.default(testEnv.orDie))
}

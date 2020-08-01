package commandcenter

import commandcenter.TestRuntime.TestEnv
import sttp.client.httpclient.zio.HttpClientZioBackend
import zio.duration._
import zio.logging.Logging
import zio.test.environment.testEnvironment
import zio.test.{ RunnableSpec, TestAspect, TestExecutor, TestRunner }
import zio.{ Layer, ZEnv }

trait CommandSpec extends RunnableSpec[TestEnv, Any] {
  val testEnv: Layer[Throwable, TestEnv] =
    testEnvironment >>> (
      testEnvironment
        ++ Logging.console((_, logEntry) => logEntry)
        ++ HttpClientZioBackend.layer()
    )

  override def aspects: List[TestAspect[Nothing, TestEnv, Nothing, Any]] =
    List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[TestEnv, Any] =
    TestRunner(TestExecutor.default(testEnv.orDie))
}

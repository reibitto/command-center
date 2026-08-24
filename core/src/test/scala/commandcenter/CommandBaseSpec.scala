package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.ToolsLive
import commandcenter.CCRuntime.Env
import zio.*
import zio.test.{
  testClock,
  TestAspect,
  TestAspectAtLeastR,
  TestAspectPoly,
  TestEnvironment,
  TestFailure,
  TestSuccess,
  ZIOSpec
}

import java.util.Locale

trait CommandBaseSpec extends ZIOSpec[TestEnvironment & Env] {

  // Every test defaults to the live clock so that a test which doesn't explicitly manage `TestClock` just runs in
  // real time, instead of silently hanging forever on a virtual clock nobody ever advances. Tests that want
  // deterministic control over timing (e.g. via `TestClock.adjust`) should opt in with `@@ useTestClock`.
  override def aspects: Chunk[TestAspectAtLeastR[TestEnvironment & Env]] =
    super.aspects :+ TestAspect.withLiveClock

  override def bootstrap: ZLayer[Any, Any, TestEnvironment & Env] =
    zio.test.testEnvironment ++ CommandBaseSpec.testLayer

  val defaultCommandContext: CommandContext =
    CommandContext(Locale.ENGLISH, TestTerminal, 1.0)

  def eventuallySucceed(timeout: Duration): Schedule[Any, Any, Duration] =
    Schedule.spaced(10.millis) zipRight Schedule.elapsed.whileOutput(_ < timeout)

  val useTestClock: TestAspectPoly =
    new TestAspect.PerTest.Poly {
      def perTest[R, E](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
          trace: Trace
      ): ZIO[R, TestFailure[E], TestSuccess] =
        testClock.flatMap(tc => test.withClock(tc))
    }
}

object CommandBaseSpec {

  val testLayer: ZLayer[Any, Any, Env] =
    Runtime.setExecutor(Executor.makeDefault(autoBlocking = false)) >>>
      Runtime.removeDefaultLoggers >>>
      ZLayer.make[Env](
        ConfigFake.layer,
        Shortcuts.unsupported,
        ToolsLive.make,
        SttpLive.make,
        CCLogging.addLoggerFor(TerminalType.Test),
        Runtime.setUnhandledErrorLogLevel(LogLevel.Warning)
      )
}

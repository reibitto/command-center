package commandcenter.util

import commandcenter.CCRuntime.Env
import commandcenter.CommandBaseSpec
import zio.*
import zio.test.*

import java.util.concurrent.atomic.AtomicInteger

object DebouncerSpec extends CommandBaseSpec {

  def spec: Spec[TestEnvironment & Env, Any] =
    suite("DebouncerSpec")(
      test("only run once when multiple requests happen in parallel") {
        val countRef = new AtomicInteger(0)

        for {
          debouncer <- Debouncer.make[Env, Nothing, Unit](10.millis, None)
          _         <- ZIO.foreachParDiscard(1 to 10) { _ =>
                 debouncer(
                   ZIO.succeed(countRef.incrementAndGet())
                 )
               }
          _ <- ZIO.attempt {
                 scala.Predef.assert(countRef.get() == 1)
               }.retry(eventuallySucceed(5.seconds))
        } yield assertTrue(countRef.get() == 1)
      },
      test("interrupt operation in progress if newer debounced request comes in") {
        val startedRef = new AtomicInteger(0)
        val finishedRef = new AtomicInteger(0)

        def doOperation =
          for {
            _ <- ZIO.succeed(startedRef.incrementAndGet())
            _ <- ZIO.sleep(100.millis)
            _ <- ZIO.succeed(finishedRef.incrementAndGet())
          } yield ()

        for {
          debouncer <- Debouncer.make[Env, Nothing, Unit](10.millis, None)
          fiber1    <- debouncer(doOperation).fork
          _         <- ZIO.sleep(50.millis)
          fiber2    <- debouncer(doOperation).fork
          _         <- ZIO.sleep(50.millis)
          fiber3    <- debouncer(doOperation).fork
          _         <- ZIO.sleep(50.millis)
          _         <- fiber1.join
          _         <- fiber2.join
          _         <- fiber3.join
          _         <- ZIO.sleep(110.millis)
        } yield assertTrue(startedRef.get() == 3, finishedRef.get() == 1)
      }
    ) @@ TestAspect.withLiveClock
}

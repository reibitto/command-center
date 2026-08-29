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
      // Uses the virtual TestClock (rather than withLiveClock) so the interleaving below is
      // deterministic instead of depending on real OS thread scheduling.
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
          _         <- TestClock.adjust(50.millis)
          fiber2    <- debouncer(doOperation).fork
          _         <- TestClock.adjust(50.millis)
          fiber3    <- debouncer(doOperation).fork
          _         <- TestClock.adjust(50.millis)
          _         <- fiber1.join
          _         <- fiber2.join
          _         <- fiber3.join
          _         <- TestClock.adjust(110.millis)
        } yield assertTrue(startedRef.get() == 3, finishedRef.get() == 1)
      } @@ useTestClock,
      test("triggerNowAwait does not hang when the debounced op times out") {
        for {
          debouncer <- Debouncer.make[Env, Nothing, Unit](10.millis, Some(50.millis))
          _         <- debouncer(ZIO.never)
          _         <- ZIO.sleep(20.millis) // let the delay elapse so the op actually starts
          result    <- debouncer.triggerNowAwait.timeout(2.seconds)
        } yield assertTrue(result.isDefined)
      },
      test("triggerNowAwait does not hang when the debounced op fails") {
        for {
          debouncer <- Debouncer.make[Env, String, Unit](10.millis, None)
          _         <- debouncer(ZIO.fail("boom"))
          _         <- ZIO.sleep(20.millis)
          result    <- debouncer.triggerNowAwait.either.timeout(2.seconds)
        } yield assertTrue(result.isDefined)
      },
      test("triggerNowAwait does not hang when the debounced op is interrupted via interruptSearch") {
        for {
          debouncer <- Debouncer.make[Env, Nothing, Unit](10.millis, None)
          _         <- debouncer(ZIO.never)
          _         <- ZIO.sleep(20.millis)
          fiber     <- debouncer.triggerNowAwait.fork
          _         <- ZIO.sleep(20.millis)
          _         <- debouncer.interruptSearch
          result    <- fiber.join.timeout(2.seconds)
        } yield assertTrue(result.isDefined)
      },
      test("triggerNowAwait does not hang when a fiber is interrupted before it starts running") {
        ZIO
          .foreach(1 to 200) { _ =>
            for {
              debouncer <- Debouncer.make[Env, Nothing, Unit](1.day, None)
              _         <- debouncer(ZIO.unit)
              result    <- debouncer.interruptSearch.zipRight(debouncer.triggerNowAwait).timeout(2.seconds)
            } yield result
          }
          .map(results => assertTrue(results.forall(_.isDefined)))
      },
      test("random concurrent apply/triggerNow/interruptSearch never hangs or crashes") {
        val rng = new scala.util.Random(42)

        def randomAction(debouncer: Debouncer[Env, Nothing, Int], counter: AtomicInteger): URIO[Env, Unit] =
          rng.nextInt(4) match {
            case 0 =>
              debouncer(
                ZIO.succeed(counter.incrementAndGet()).delay(rng.nextInt(20).millis)
              ).unit
            case 1 => debouncer.triggerNowAwait
            case 2 => debouncer.interruptSearch
            case _ =>
              // Occasionally feed an op that dies outright, to make sure that doesn't wedge anything either.
              debouncer(ZIO.succeed(if (rng.nextBoolean()) throw new RuntimeException("boom") else counter.get())).unit
          }

        ZIO
          .foreach(1 to 30) { _ =>
            for {
              debouncer <- Debouncer.make[Env, Nothing, Int](5.millis, Some(15.millis))
              counter = new AtomicInteger(0)
              r1 <- ZIO.foreachParDiscard(1 to 40)(_ => randomAction(debouncer, counter)).timeout(3.seconds)
              r2 <- debouncer.triggerNowAwait.timeout(3.seconds) // final drain must not hang
            } yield (r1, r2)
          }
          .map(results => assertTrue(results.forall { case (r1, r2) => r1.isDefined && r2.isDefined }))
      }
    )
}

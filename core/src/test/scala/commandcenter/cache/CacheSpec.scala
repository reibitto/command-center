package commandcenter.cache

import zio.*
import zio.test.*

import java.util.concurrent.atomic.AtomicInteger

object CacheSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] =
    suite("CacheSpec")(
      test("only perform underlying operation once when receiving multiple parallel requests") {
        val cache = ZCache.make[String, String]()
        val callsToMake = 10
        val expensiveGetOperationsCalled = new AtomicInteger(0)

        def simulateExpensiveGetOperation: UIO[String] =
          for {
            _ <- ZIO.sleep(1.second)
            _ = expensiveGetOperationsCalled.incrementAndGet()
          } yield scala.util.Random.nextInt().toString

        for {
          results <- ZIO.foreachPar((1 to callsToMake).toVector) { _ =>
                       cache.getOrElseUpdate("some-expensive-op-key", 15.minutes)(simulateExpensiveGetOperation)
                     }
        } yield assertTrue(
          results.length == callsToMake,
          results.distinct.length == 1,
          expensiveGetOperationsCalled.get == 1
        )
      }
    ) @@ TestAspect.withLiveClock
}

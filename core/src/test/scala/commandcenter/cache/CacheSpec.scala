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
      },
      test("set expiration per key") {
        val cache = ZCache.make[String, String]()

        for {
          _    <- cache.set("foo1", "bar1", 1.second)
          _    <- cache.set("foo2", "bar2", 2.second)
          _    <- cache.set("foo3", "bar3", 3.second)
          foo1 <- cache.get("foo1")
          foo2 <- cache.get("foo2")
          foo3 <- cache.get("foo3")
          foo4 <- cache.get("foo4")
          _    <- assertTrue(
                 foo1.get == "bar1",
                 foo2.get == "bar2",
                 foo3.get == "bar3",
                 foo4.isEmpty
               )
          _    <- ZIO.sleep(1.second)
          foo1 <- cache.get("foo1")
          foo2 <- cache.get("foo2")
          foo3 <- cache.get("foo3")
          foo4 <- cache.get("foo4")
          // 1st entry should expire after 1 second
          _ <- assertTrue(
                 foo1.isEmpty,
                 foo2.get == "bar2",
                 foo3.get == "bar3",
                 foo4.isEmpty
               )
          _    <- ZIO.sleep(1.second)
          foo1 <- cache.get("foo1")
          foo2 <- cache.get("foo2")
          foo3 <- cache.get("foo3")
          foo4 <- cache.get("foo4")
          // 2nd entry should expire after 1 more second
          _ <- assertTrue(
                 foo1.isEmpty,
                 foo2.isEmpty,
                 foo3.get == "bar3",
                 foo4.isEmpty
               )
          _    <- ZIO.sleep(1.second)
          foo1 <- cache.get("foo1")
          foo2 <- cache.get("foo2")
          foo3 <- cache.get("foo3")
          foo4 <- cache.get("foo4")
          // 3rd entry should expire after 1 more second
          _ <- assertTrue(
                 foo1.isEmpty,
                 foo2.isEmpty,
                 foo3.isEmpty,
                 foo4.isEmpty
               )
        } yield assertCompletes
      }
    ) @@ TestAspect.withLiveClock
}

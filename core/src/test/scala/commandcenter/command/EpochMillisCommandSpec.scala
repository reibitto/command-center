package commandcenter.command

import commandcenter.CommandBaseSpec
import zio.test.*

import java.time.Instant

object EpochMillisCommandSpec extends CommandBaseSpec {
  val command: EpochMillisCommand = EpochMillisCommand(List("epochmillis"))

  def spec =
    suite("EpochMillisCommandSpec")(
      test("get current time") {
        val time = Instant.now()

        for {
          _       <- TestClock.setTime(time)
          results <- Command.search(Vector(command), Map.empty, "epochmillis", defaultCommandContext)
          previews = results.previews
        } yield assertTrue(previews.head.asInstanceOf[PreviewResult.Some[Any]].result == time.toEpochMilli.toString)
      },
      test("return nothing for non-matching search") {
        for {
          results <- Command.search(Vector(command), Map.empty, "not matching", defaultCommandContext)
        } yield assertTrue(results.previews.isEmpty)
      }
    )
}

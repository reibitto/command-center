package commandcenter.command

import commandcenter.CommandBaseSpec

import zio.test.*
import zio._

object EpochMillisCommandSpec extends CommandBaseSpec {
  val command: EpochMillisCommand = EpochMillisCommand(List("epochmillis"))

  def spec =
    suite("EpochMillisCommandSpec")(
      test("get current time") {
        for {
          _       <- TestClock.setTime(5.seconds)
          results <- Command.search(Vector(command), Map.empty, "epochmillis", defaultCommandContext)
          previews = results.previews
        } yield assertTrue(previews.head.asInstanceOf[PreviewResult.Some[Any]].result == "5000")
      },
      test("return nothing for non-matching search") {
        for {
          results <- Command.search(Vector(command), Map.empty, "not matching", defaultCommandContext)
        } yield assertTrue(results.previews.isEmpty)
      }
    )
}

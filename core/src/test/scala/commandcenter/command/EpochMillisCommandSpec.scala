package commandcenter.command

import commandcenter.CommandBaseSpec
import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock

object EpochMillisCommandSpec extends CommandBaseSpec {
  val command: EpochMillisCommand = EpochMillisCommand(List("epochmillis"))

  def spec =
    suite("EpochMillisCommandSpec")(
      testM("get current time") {
        val results = Command.search(Vector(command), Map.empty, "epochmillis", defaultCommandContext)

        for {
          _        <- TestClock.setTime(5.seconds)
          previews <- results.map(_.previews)
        } yield assert(previews)(hasFirst(hasField("result", _.result, equalTo("5000"))))
      },
      testM("return nothing for non-matching search") {
        val results = Command.search(Vector(command), Map.empty, "not matching", defaultCommandContext)

        assertM(results.map(_.previews))(isEmpty)
      }
    )
}

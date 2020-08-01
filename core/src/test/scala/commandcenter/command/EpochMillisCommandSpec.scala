package commandcenter.command

import commandcenter.CommandSpec
import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock

object EpochMillisCommandSpec extends CommandSpec {
  val command: EpochMillisCommand = EpochMillisCommand()

  def spec =
    suite("EpochMillisCommandSpec")(
      testM("get current time") {
        val results = Command.search(Vector(command), Map.empty, "epoch", defaultCommandContext)

        for {
          _        <- TestClock.setTime(5.seconds)
          previews <- results.map(_.previews)
        } yield assert(previews)(hasFirst(hasField("result", _.result, equalTo(5000L))))
      },
      testM("return nothing for non-matching search") {
        val results = Command.search(Vector(command), Map.empty, "not matching", defaultCommandContext)

        assertM(results.map(_.previews))(isEmpty)
      }
    )
}

package commandcenter

import commandcenter.CCRuntime.Env
import commandcenter.command._
import zio.ZIO
import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock

object SearchSpec extends CommandBaseSpec {
  val defectCommand: Command[Unit] = new Command[Unit] {
    val commandType: CommandType = CommandType.ExitCommand

    def commandNames: List[String] = List("exit")

    def title: String = "Exit"

    def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
      ZIO.dieMessage("This command is broken!")
  }

  def spec =
    suite("SearchSpec")(
      testM("defect in one command should not fail entire search") {
        val commands = Vector(defectCommand, EpochMillisCommand(List("epochmillis")))
        val results  = Command.search(commands, Map.empty, "e", defaultCommandContext)

        for {
          _        <- TestClock.setTime(1.second)
          previews <- results.map(_.previews)
        } yield assert(previews)(hasFirst(hasField("result", _.result, equalTo(1000L.asInstanceOf[AnyVal]))))
      }
    )
}

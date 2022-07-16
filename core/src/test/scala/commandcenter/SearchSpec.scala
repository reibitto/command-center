package commandcenter

import commandcenter.command.*
import commandcenter.CCRuntime.Env
import zio.duration.*
import zio.test.*
import zio.test.environment.TestClock
import zio.ZIO

object SearchSpec extends CommandBaseSpec {

  val defectCommand: Command[Unit] = new Command[Unit] {
    val commandType: CommandType = CommandType.ExitCommand

    def commandNames: List[String] = List("exit")

    def title: String = "Exit"

    def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
      ZIO.dieMessage("This command is broken!")
  }

  def spec =
    suite("SearchSpec")(
      testM("defect in one command should not fail entire search") {
        val commands = Vector(defectCommand, EpochMillisCommand(List("epochmillis")))
        val results = Command.search(commands, Map.empty, "e", defaultCommandContext)

        for {
          _        <- TestClock.setTime(1.second)
          previews <- results.map(_.previews)
        } yield assertTrue(previews.head.asInstanceOf[PreviewResult.Some[Any]].result == "1000")
      }
    )
}

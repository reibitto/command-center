package commandcenter

import commandcenter.command.*
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import zio.*
import zio.test.*

import java.time.Instant

object SearchSpec extends CommandBaseSpec {

  val defectCommand: Command[Unit] = new Command[Unit] {
    val commandType: CommandType = CommandType.ExitCommand

    def commandNames: List[String] = List("exit")

    def title: String = "Exit"

    def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
      ZIO.dieMessage("This command is intentionally broken!")
  }

  // A minimal command that records its own name to `ranOrder` when run, so that tests can assert on the order
  // multiple commands actually ran in.
  def trackingCommand(name: String, ranOrder: Ref[List[String]]): Command[String] =
    new Command[String] {
      val commandType: CommandType = CommandType.ExitCommand

      def commandNames: List[String] = List(name)

      def title: String = name

      def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
        ZIO
          .fromOption(searchInput.asArgs)
          .orElseFail(CommandError.NotApplicable)
          .as(PreviewResults.one(Preview(name).onRun(ranOrder.update(_ :+ name))))
    }

  // A command that always matches, unconditionally, with a low score - simulating the handful of unrelated
  // commands that fuzzy-match almost any short input via `asKeyword`.
  def noiseCommand(name: String): Command[String] =
    new Command[String] {
      val commandType: CommandType = CommandType.ExitCommand

      def commandNames: List[String] = List(name)

      def title: String = name

      def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
        ZIO.succeed(PreviewResults.one(Preview(name).score(Scores.low)))
    }

  def spec: Spec[TestEnvironment & Env, Any] =
    suite("SearchSpec")(
      test("defect in one command should not fail entire search") {
        val commands = Vector(defectCommand, EpochMillisCommand(List("epochmillis")))
        val results = Command.search(commands, Map.empty, "e", defaultCommandContext)
        val time = Instant.now()

        for {
          _        <- TestClock.setTime(time)
          previews <- results.map(_.previews)
        } yield assertTrue(previews.head.asInstanceOf[PreviewResult.Some[Any]].result == time.toEpochMilli.toString)
      } @@ useTestClock,
      suite("alias expansion")(
        test("a single alias name mapping to multiple targets produces a result for each target") {
          val aliases = Map("hex" -> List("radix --to 16", "radix --from 16"))
          val commands = Vector(RadixCommand(List("radix")))

          for {
            results <- Command.search(commands, aliases, "hex 20", defaultCommandContext)
            rendered = results.previews.map(_.renderFn()).collect { case Rendered.Ansi(s) => s.plainText }
          } yield assertTrue(
            results.previews.length == 2,
            rendered.exists(_.contains("14")), // 20 (decimal) converted to base 16
            rendered.exists(_.contains("32")) // 20 (base 16) converted to decimal
          )
        },
        test("an alias with a single target still produces exactly one result") {
          val aliases = Map("b" -> List("epochmillis"))
          val commands = Vector(EpochMillisCommand(List("epochmillis")))

          for {
            results <- Command.search(commands, aliases, "b", defaultCommandContext)
          } yield assertTrue(results.previews.length == 1)
        }
      ),
      suite("semicolon-separated multi-statement input")(
        test("each statement's results are concatenated, in order, alongside a combined run-all entry") {
          for {
            order   <- Ref.make(List.empty[String])
            results <- Command.search(
                         Vector(trackingCommand("cmda", order), trackingCommand("cmdb", order)),
                         Map.empty,
                         "cmda 1; cmdb 2",
                         defaultCommandContext
                       )
          } yield assertTrue(results.previews.length == 3) // combined entry + cmda's result + cmdb's result
        },
        test("running the combined entry runs every statement's top match in order") {
          for {
            order   <- Ref.make(List.empty[String])
            results <- Command.search(
                         Vector(trackingCommand("cmda", order), trackingCommand("cmdb", order)),
                         Map.empty,
                         "cmda 1; cmdb 2",
                         defaultCommandContext
                       )
            _        <- results.previews.head.onRun
            ranOrder <- order.get
          } yield assertTrue(ranOrder == List("cmda", "cmdb"))
        },
        test("a semicolon inside quotes doesn't split the statement") {
          for {
            order   <- Ref.make(List.empty[String])
            results <- Command.search(
                         Vector(trackingCommand("cmda", order)),
                         Map.empty,
                         """cmda "a;b"""",
                         defaultCommandContext
                       )
          } yield assertTrue(results.previews.length == 1) // one statement, one result, no combined entry
        },
        test("no combined entry is synthesized when one of the statements doesn't match anything") {
          for {
            order   <- Ref.make(List.empty[String])
            results <- Command.search(
                         Vector(trackingCommand("cmda", order)),
                         Map.empty,
                         "cmda 1; doesnotexist 2",
                         defaultCommandContext
                       )
          } yield assertTrue(results.previews.length == 1) // only cmda's own result
        },
        test("a trailing semicolon with nothing after it is treated as a single statement") {
          for {
            order   <- Ref.make(List.empty[String])
            results <- Command.search(
                         Vector(trackingCommand("cmda", order)),
                         Map.empty,
                         "cmda 1;",
                         defaultCommandContext
                       )
          } yield assertTrue(results.previews.length == 1) // no second (empty) statement, no combined entry
        },
        test("an alias target that itself contains semicolon-separated statements is split and run like any other") {
          val aliases = Map("ss" -> List("cmda 1; cmdb 2; cmda 3"))

          for {
            order   <- Ref.make(List.empty[String])
            results <- Command.search(
                         Vector(trackingCommand("cmda", order), trackingCommand("cmdb", order)),
                         aliases,
                         "ss",
                         defaultCommandContext
                       )
            _        <- results.previews.head.onRun
            ranOrder <- order.get
          } yield assertTrue(
            results.previews.length == 4, // combined entry + 3 individual statement results
            ranOrder == List("cmda", "cmdb", "cmda")
          )
        },
        test("an exact alias match ranks above many low-scoring unrelated matches") {
          // Regression test: previews used to only be sorted by score *within* a single alias candidate /
          // statement group, then naively concatenated across candidates - so a very-high-scoring alias
          // expansion could still end up buried beneath a pile of low-scoring unrelated matches for the same
          // raw input, instead of sorting to the top overall.
          val aliases = Map("ss" -> List("cmda 1; cmdb 2; cmda 3"))
          val noiseCommands = (1 to 30).map(i => noiseCommand(s"noise$i")).toVector

          for {
            order   <- Ref.make(List.empty[String])
            results <- Command.search(
                         Vector(trackingCommand("cmda", order), trackingCommand("cmdb", order)) ++ noiseCommands,
                         aliases,
                         "ss",
                         defaultCommandContext
                       )
          } yield assertTrue(results.previews.headOption.exists(_.score > Scores.low))
        }
      )
    )
}

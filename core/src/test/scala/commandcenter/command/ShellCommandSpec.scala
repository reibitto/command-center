package commandcenter.command

import com.typesafe.config.ConfigFactory
import commandcenter.util.OS
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import commandcenter.CommandBaseSpec
import zio.test.*

object ShellCommandSpec extends CommandBaseSpec {

  val command: ShellCommand =
    ShellCommand("Echo", "echo {query}", ShellOutputMode.Silent, runAsAdmin = false, List("echo"), Set.empty)

  def spec: Spec[TestEnvironment & Env, Any] =
    suite("ShellCommandSpec")(
      test("matches its alias") {
        for {
          results <- Command.search(Vector(command), Map.empty, "echo hello world", defaultCommandContext)
        } yield assertTrue(results.previews.nonEmpty)
      },
      test("substitutes {query} with the text following the alias") {
        for {
          results <- Command.search(Vector(command), Map.empty, "echo hello world", defaultCommandContext)
          rendered = results.previews.head.asInstanceOf[PreviewResult.Some[Unit]].renderFn()
          plainText = rendered.asInstanceOf[Rendered.Ansi].ansiStr.plainText
        } yield assertTrue(plainText.contains("echo hello world"))
      },
      test("return nothing for non-matching search") {
        for {
          results <- Command.search(Vector(command), Map.empty, "not matching", defaultCommandContext)
        } yield assertTrue(results.previews.isEmpty)
      },
      test("make() allows runAsAdmin combined with silent or window output") {
        def config(outputMode: String) =
          ConfigFactory.parseString(s"""command: "whoami"
                                       |outputMode: "$outputMode"
                                       |runAsAdmin: true
                                       |""".stripMargin)

        for {
          silent <- ShellCommand.make(config("silent"))
          window <- ShellCommand.make(config("window"))
        } yield assertTrue(silent.runAsAdmin, window.runAsAdmin)
      },
      test("make() rejects runAsAdmin combined with an output-capturing mode on Windows") {
        val config = ConfigFactory.parseString("""command: "whoami"
                                                 |outputMode: "clipboard"
                                                 |runAsAdmin: true
                                                 |""".stripMargin)

        for {
          exit <- ShellCommand.make(config).exit
        } yield assertTrue(OS.os != OS.Windows || exit.isFailure)
      }
    )
}

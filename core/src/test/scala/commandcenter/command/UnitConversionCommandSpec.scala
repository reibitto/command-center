package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.CommandBaseSpec
import zio.test.*

object UnitConversionCommandSpec extends CommandBaseSpec {
  val command: UnitConversionCommand = UnitConversionCommand()

  def spec: Spec[TestEnvironment & Env, Any] =
    suite("UnitConversionCommandSpec")(
      test("converts a value with a unit") {
        for {
          results <- Command.search(Vector(command), Map.empty, "100 km", defaultCommandContext)
        } yield assertTrue(results.previews.nonEmpty)
      },
      test("does not match (or throw) on a math expression") {
        for {
          results <- Command.search(Vector(command), Map.empty, "3000*10", defaultCommandContext)
        } yield assertTrue(results.previews.isEmpty)
      }
    )
}

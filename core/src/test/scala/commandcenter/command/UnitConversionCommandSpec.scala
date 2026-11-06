package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.CommandBaseSpec
import zio.test.*

object UnitConversionCommandSpec extends CommandBaseSpec {
  val command: UnitConversionCommand = UnitConversionCommand()

  def rendered(input: String): List[String] =
    command.parseQuantity(input).toList.flatMap { case (number, unit) =>
      UnitOfMeasure.convert(number, unit).map(r => r.unitOfMeasure.render(r.value))
    }

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
      },
      test("length") {
        assertTrue(
          rendered("10 miles") == List("16.1 km"),
          rendered("100 km") == List("62.1 mi"),
          rendered("5 ft").contains("152.4 cm"),
          rendered("180 cm").contains("5 ft 10.9 in"),
          rendered("1 yd").contains("3 ft")
        )
      },
      test("speed") {
        assertTrue(
          rendered("60 mph") == List("96.6 km/h"),
          rendered("100 kph") == List("62.1 mph"),
          rendered("100 km/h") == List("62.1 mph"),
          rendered("10 knots") == List("18.5 km/h", "11.5 mph")
        )
      },
      test("mass") {
        assertTrue(
          rendered("1 lb") == List("0.5 kg", "453.6 g"),
          rendered("100 g") == List("3.53 oz"),
          rendered("10 st").contains("63.5 kg")
        )
      },
      test("baking volumes") {
        assertTrue(
          rendered("1 cup") == List("236.6 ml", "8 fl oz", "16 tbsp"),
          rendered("3 tsp").contains("1 tbsp"),
          rendered("8 fl oz").contains("236.6 ml"),
          rendered("2 fluid ounces").contains("4 tbsp"),
          rendered("1 gallon").contains("4 qt")
        )
      },
      test("fractions") {
        assertTrue(
          rendered("1/2 cup").contains("118.3 ml"),
          rendered("1 1/2 cups").contains("354.9 ml"),
          rendered("1/0 cup").isEmpty
        )
      },
      test("temperature") {
        assertTrue(
          rendered("-40 C") == List("-40 F"),
          rendered("212 F") == List("100 C"),
          rendered("273.15 kelvin") == List("0 C", "32 F")
        )
      },
      test("data") {
        assertTrue(rendered("1 GB").contains("1,000 MB"))
      },
      test("unknown units are not applicable") {
        assertTrue(rendered("5 foo").isEmpty, rendered("100k").isEmpty)
      }
    )
}

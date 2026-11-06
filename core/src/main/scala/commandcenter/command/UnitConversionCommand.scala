package commandcenter.command

import com.typesafe.config.Config
import commandcenter.command.CommandError.*
import commandcenter.tools.Tools
import commandcenter.util.TextFormats.*
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.*

import java.text.DecimalFormatSymbols
import scala.util.matching.Regex

final case class UnitConversionCommand() extends Command[Double] {
  val commandType: CommandType = CommandType.UnitConversionCommand

  val commandNames: List[String] = List.empty

  val title: String = "Unit Conversion"

  private def decimalSeparator: Char = DecimalFormatSymbols.getInstance.getDecimalSeparator

  private def groupingSeparator: Char = DecimalFormatSymbols.getInstance.getGroupingSeparator

  // Backslash-escape these since they are interpolated directly into the regex below and, depending on the locale,
  // may otherwise be interpreted as regex metacharacters (e.g. "." matching any character instead of a literal dot).
  private def escapedGroupingSeparator: String = s"\\$groupingSeparator"
  private def escapedDecimalSeparator: String = s"\\$decimalSeparator"

  // sign, number, fraction, unit ("100 km", "1 1/2 cups"). Units can't start with "/" so "1/2" parses as a fraction.
  val unitRegex: Regex =
    s"""^([+-])?(?:([\\d$escapedGroupingSeparator]+(?:$escapedDecimalSeparator\\d+)?)\\s*)?(?:(\\d+)/(\\d+))?\\s*([^\\d\\s/].*)$$""".r

  def parseQuantity(input: String): Option[(Double, UnitOfMeasure)] =
    unitRegex.unapplySeq(input.trim).flatMap {
      case List(sign, whole, numerator, denominator, unitText) if whole != null || numerator != null =>
        for {
          wholeValue <- Option(whole).fold(Option(0.0)) { w =>
                          w.replace(groupingSeparator.toString, "").replace(decimalSeparator, '.').toDoubleOption
                        }
          fractionValue <- Option(numerator).fold(Option(0.0)) { n =>
                             Option.when(denominator.toDouble != 0)(n.toDouble / denominator.toDouble)
                           }
          unit <- UnitOfMeasure.parse(unitText)
        } yield ((if (sign == "-") -1 else 1) * (wholeValue + fractionValue), unit)

      case _ => None
    }

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Double]] =
    ZIO.fromOption(parseQuantity(searchInput.input)).orElseFail(NotApplicable).map { case (number, unit) =>
      PreviewResults.fromIterable(UnitOfMeasure.convert(number, unit).map { r =>
        val formatted = r.unitOfMeasure.render(r.value)

        Preview(r.value)
          .score(Scores.veryHigh(searchInput.context))
          .rendered(Renderer.renderDefault(title, formatted))
          .onRun(Tools.setClipboard(formatted))
      })
    }
}

object UnitConversionCommand extends CommandPlugin[UnitConversionCommand] {
  def make(config: Config): UIO[UnitConversionCommand] = ZIO.succeed(UnitConversionCommand())
}

final case class ConversionResult(value: Double, unitOfMeasure: UnitOfMeasure)

sealed trait Dimension

object Dimension {
  case object Length extends Dimension
  case object Speed extends Dimension
  case object Mass extends Dimension
  case object Volume extends Dimension
  case object Temperature extends Dimension
  case object Data extends Dimension
}

/** A unit is converted through its dimension's base unit: `base = (value +
  * offset) * factor`. `offset` is only needed for temperatures.
  */
class UnitOfMeasure(
    val symbol: String,
    val dimension: Dimension,
    val factor: Double,
    val digits: Int,
    val names: Set[String],
    val offset: Double = 0.0
) {
  def toBase(value: Double): Double = (value + offset) * factor

  def fromBase(base: Double): Double = base / factor - offset

  def render(value: Double): String = s"${value.digits(digits)} $symbol"

  override def toString: String = symbol
}

object UnitOfMeasure {
  import Dimension.*

  private def unit(symbol: String, dimension: Dimension, factor: Double, digits: Int, names: String*): UnitOfMeasure =
    new UnitOfMeasure(symbol, dimension, factor, digits, (symbol +: names).map(_.toLowerCase).toSet)

  // Length (base: meter)
  val Millimeter: UnitOfMeasure = unit("mm", Length, 0.001, 1, "millimeter", "millimeters", "millimetre", "millimetres")
  val Centimeter: UnitOfMeasure = unit("cm", Length, 0.01, 1, "centimeter", "centimeters", "centimetre", "centimetres")
  val Meter: UnitOfMeasure = unit("m", Length, 1, 2, "meter", "meters", "metre", "metres")
  val Kilometer: UnitOfMeasure = unit("km", Length, 1000, 1, "kilometer", "kilometers", "kilometre", "kilometres")
  val Inch: UnitOfMeasure = unit("in", Length, 0.0254, 1, "inch", "inches", "\"", "″")
  val Foot: UnitOfMeasure = unit("ft", Length, 0.3048, 2, "foot", "feet", "'", "′")
  val Yard: UnitOfMeasure = unit("yd", Length, 0.9144, 2, "yds", "yard", "yards")
  val Mile: UnitOfMeasure = unit("mi", Length, 1609.344, 1, "mile", "miles")

  // Rendering-only unit: the value is in inches, shown as e.g. "5 ft 11 in"
  val FeetAndInches: UnitOfMeasure = new UnitOfMeasure("ft in", Length, 0.0254, 1, Set.empty) {
    override def render(value: Double): String = {
      val totalInches = math.round(value * 10) / 10.0
      val feet = (totalInches / 12).toInt

      s"$feet ft ${(totalInches - feet * 12).digits(1)} in"
    }
  }

  // Speed (base: meters per second)
  val KilometersPerHour: UnitOfMeasure = unit("km/h", Speed, 1 / 3.6, 1, "kph", "kmh", "kmph", "km/hr")
  val MilesPerHour: UnitOfMeasure = unit("mph", Speed, 0.44704, 1, "mi/h", "mi/hr")
  val MetersPerSecond: UnitOfMeasure = unit("m/s", Speed, 1, 2, "mps")
  val Knot: UnitOfMeasure = unit("kn", Speed, 1852 / 3600.0, 1, "kt", "kts", "knot", "knots")

  // Mass (base: gram)
  val Gram: UnitOfMeasure = unit("g", Mass, 1, 1, "gram", "grams", "gramme", "grammes")
  val Kilogram: UnitOfMeasure = unit("kg", Mass, 1000, 1, "kgs", "kilo", "kilos", "kilogram", "kilograms")
  val Ounce: UnitOfMeasure = unit("oz", Mass, 28.349523125, 2, "ounce", "ounces")
  val Pound: UnitOfMeasure = unit("lbs", Mass, 453.59237, 1, "lb", "pound", "pounds")
  val Stone: UnitOfMeasure = unit("st", Mass, 6350.29318, 2, "stone", "stones")

  // Volume (base: milliliter). US customary units.
  val Milliliter: UnitOfMeasure =
    unit("ml", Volume, 1, 1, "cc", "milliliter", "milliliters", "millilitre", "millilitres")
  val Liter: UnitOfMeasure = unit("L", Volume, 1000, 2, "liter", "liters", "litre", "litres")
  val Teaspoon: UnitOfMeasure = unit("tsp", Volume, 4.92892159375, 2, "tsps", "teaspoon", "teaspoons")
  val Tablespoon: UnitOfMeasure = unit("tbsp", Volume, 14.78676478125, 2, "tbsps", "tbs", "tablespoon", "tablespoons")

  val FluidOunce: UnitOfMeasure =
    unit("fl oz", Volume, 29.5735295625, 2, "floz", "fl. oz", "fl.oz", "fluid ounce", "fluid ounces")
  val Cup: UnitOfMeasure = unit("cups", Volume, 236.5882365, 2, "cup")
  val Pint: UnitOfMeasure = unit("pt", Volume, 473.176473, 2, "pint", "pints")
  val Quart: UnitOfMeasure = unit("qt", Volume, 946.352946, 2, "quart", "quarts")
  val Gallon: UnitOfMeasure = unit("gal", Volume, 3785.411784, 2, "gallon", "gallons")

  // Temperature (base: kelvin)
  val Celsius: UnitOfMeasure =
    new UnitOfMeasure("C", Temperature, 1, 1, Set("c", "°c", "ｃ", "度", "ど", "ド", "celsius", "centigrade"), 273.15)

  val Fahrenheit: UnitOfMeasure =
    new UnitOfMeasure("F", Temperature, 5 / 9.0, 1, Set("f", "°f", "ｆ", "fahrenheit"), 459.67)
  // No bare "k" alias since "100k" usually means 100,000
  val Kelvin: UnitOfMeasure = new UnitOfMeasure("K", Temperature, 1, 1, Set("kelvin"))

  // Data (base: byte)
  val Byte: UnitOfMeasure = unit("bytes", Data, 1, 0, "b", "byte")
  val Kilobyte: UnitOfMeasure = unit("KB", Data, 1e3, 2)
  val Kibibyte: UnitOfMeasure = unit("KiB", Data, 1024.0, 2)
  val Megabyte: UnitOfMeasure = unit("MB", Data, 1e6, 3)
  val Mebibyte: UnitOfMeasure = unit("MiB", Data, math.pow(1024, 2), 3)
  val Gigabyte: UnitOfMeasure = unit("GB", Data, 1e9, 4)
  val Gibibyte: UnitOfMeasure = unit("GiB", Data, math.pow(1024, 3), 4)
  val Terabyte: UnitOfMeasure = unit("TB", Data, 1e12, 5)
  val Tebibyte: UnitOfMeasure = unit("TiB", Data, math.pow(1024, 4), 5)

  val all: List[UnitOfMeasure] = List(
    Millimeter,
    Centimeter,
    Meter,
    Kilometer,
    Inch,
    Foot,
    Yard,
    Mile,
    FeetAndInches,
    KilometersPerHour,
    MilesPerHour,
    MetersPerSecond,
    Knot,
    Gram,
    Kilogram,
    Ounce,
    Pound,
    Stone,
    Milliliter,
    Liter,
    Teaspoon,
    Tablespoon,
    FluidOunce,
    Cup,
    Pint,
    Quart,
    Gallon,
    Celsius,
    Fahrenheit,
    Kelvin,
    Byte,
    Kilobyte,
    Kibibyte,
    Megabyte,
    Mebibyte,
    Gigabyte,
    Gibibyte,
    Terabyte,
    Tebibyte
  )

  // Which units to show for a given source unit. Units not listed here convert to every other unit in their dimension.
  private val targets: Map[UnitOfMeasure, List[UnitOfMeasure]] = Map(
    Millimeter -> List(Inch),
    Centimeter -> List(Inch, Foot, FeetAndInches),
    Meter -> List(Foot, FeetAndInches, Yard),
    Kilometer -> List(Mile),
    Inch -> List(Centimeter, Foot),
    Foot -> List(Meter, Centimeter, Inch),
    Yard -> List(Meter, Foot),
    Mile -> List(Kilometer),
    KilometersPerHour -> List(MilesPerHour),
    MilesPerHour -> List(KilometersPerHour),
    MetersPerSecond -> List(KilometersPerHour, MilesPerHour),
    Knot -> List(KilometersPerHour, MilesPerHour),
    Gram -> List(Ounce),
    Kilogram -> List(Pound),
    Ounce -> List(Gram),
    Pound -> List(Kilogram, Gram),
    Stone -> List(Kilogram, Pound),
    Milliliter -> List(Teaspoon, Tablespoon, FluidOunce, Cup),
    Liter -> List(Cup, Quart, Gallon),
    Teaspoon -> List(Milliliter, Tablespoon),
    Tablespoon -> List(Milliliter, Teaspoon, FluidOunce),
    FluidOunce -> List(Milliliter, Tablespoon, Cup),
    Cup -> List(Milliliter, FluidOunce, Tablespoon),
    Pint -> List(Milliliter, Cup),
    Quart -> List(Liter, Cup),
    Gallon -> List(Liter, Quart),
    Celsius -> List(Fahrenheit),
    Fahrenheit -> List(Celsius),
    Kelvin -> List(Celsius, Fahrenheit)
  )

  private val byName: Map[String, UnitOfMeasure] = all.flatMap(u => u.names.map(_ -> u)).toMap

  def parse(text: String): Option[UnitOfMeasure] =
    byName.get(text.trim.toLowerCase.replaceAll("\\s+", " "))

  def convert(number: Double, source: UnitOfMeasure): List[ConversionResult] = {
    val base = source.toBase(number)

    targets
      .getOrElse(source, all.filter(u => u.dimension == source.dimension && u != source))
      .map(target => ConversionResult(target.fromBase(base), target))
  }
}

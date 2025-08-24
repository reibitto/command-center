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

  val unitRegex: Regex =
    s"""^[+-]?([0-9]{1,3}(?:$groupingSeparator[0-9]{3})*(?:\\$decimalSeparator[0-9]+)?|\\d*\\.\\d+|\\d+)\\s*(.+)$$""".r

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Double]] =
    for {
      conversionResults <- ZIO.fromOption {
                             unitRegex.unapplySeq(searchInput.input.trim).flatMap {
                               case List(value, sourceUnit) =>
                                 val number = value.replace(groupingSeparator.toString, "").toDouble

                                 UnitOfMeasure.parse(sourceUnit).map { unit =>
                                   UnitOfMeasure.convert(number, unit)
                                 }

                               case _ => None
                             }
                           }.orElseFail(NotApplicable)
      previewResults = conversionResults.map { r =>
                         val formatted = r.unitOfMeasure.render(r.value)

                         Preview(r.value)
                           .score(Scores.veryHigh(searchInput.context))
                           .rendered(Renderer.renderDefault(title, formatted))
                           .onRun(Tools.setClipboard(formatted))
                       }
    } yield PreviewResults.fromIterable(previewResults)
}

object UnitConversionCommand extends CommandPlugin[UnitConversionCommand] {
  def make(config: Config): UIO[UnitConversionCommand] = ZIO.succeed(UnitConversionCommand())
}

final case class ConversionResult(value: Double, unitOfMeasure: UnitOfMeasure)

sealed trait UnitOfMeasure {
  def render(value: Double): String
}

object UnitOfMeasure {

  case object Celsius extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(1)} C"
  }

  case object Fahrenheit extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(1)} F"
  }

  case object Kilometer extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(1)} km"
  }

  case object Mile extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(1)} mi"
  }

  case object Byte extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(0)} bytes"
  }

  case object Kilobyte extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(2)} KB"
  }

  case object Kibibyte extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(2)} KiB"
  }

  case object Megabyte extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(3)} MB"
  }

  case object Mebibyte extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(3)} MiB"
  }

  case object Gigabyte extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(4)} GB"
  }

  case object Gibibyte extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(4)} GiB"
  }

  case object Terabyte extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(5)} TB"
  }

  case object Tebibyte extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(5)} TiB"
  }

  case object Pound extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(1)} lbs"
  }

  case object Kilogram extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(1)} kg"
  }

  case object Feet extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(2)} ft"
  }

  case object Inches extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(1)} in"
  }

  case object FeetAndInches extends UnitOfMeasure {

    // `value` is in inches
    def render(value: Double): String = {
      val feet = value.toInt / 12
      val inches = value % 12

      s"$feet ft ${inches.digits(1)} in"
    }
  }

  case object Centimeter extends UnitOfMeasure {
    def render(value: Double): String = s"${value.digits(1)} cm"
  }

  def parse(text: String): Option[UnitOfMeasure] =
    text.trim.toLowerCase match {
      case "c" | "Ｃ" | "度" | "ど" | "ド" => Some(UnitOfMeasure.Celsius)
      case "f" | "Ｆ"                   => Some(UnitOfMeasure.Fahrenheit)
      case "km"                        => Some(UnitOfMeasure.Kilometer)
      case "mi" | "mile" | "miles"     => Some(UnitOfMeasure.Mile)
      case "b" | "byte" | "bytes"      => Some(UnitOfMeasure.Byte)
      case "kb"                        => Some(UnitOfMeasure.Kilobyte)
      case "kib"                       => Some(UnitOfMeasure.Kibibyte)
      case "mb"                        => Some(UnitOfMeasure.Megabyte)
      case "mib"                       => Some(UnitOfMeasure.Mebibyte)
      case "gb"                        => Some(UnitOfMeasure.Gigabyte)
      case "gib"                       => Some(UnitOfMeasure.Gibibyte)
      case "tb"                        => Some(UnitOfMeasure.Terabyte)
      case "tib"                       => Some(UnitOfMeasure.Tebibyte)
      case "lb" | "lbs"                => Some(UnitOfMeasure.Pound)
      case "kg"                        => Some(UnitOfMeasure.Kilogram)
      case "ft"                        => Some(UnitOfMeasure.Feet)
      case "in"                        => Some(UnitOfMeasure.Inches)
      case "cm"                        => Some(UnitOfMeasure.Centimeter)
      case _                           => None
    }

  def convertFromBytes(bytes: Double): Seq[ConversionResult] =
    Seq(
      ConversionResult(bytes, UnitOfMeasure.Byte),
      ConversionResult(bytes / 1000, UnitOfMeasure.Kilobyte),
      ConversionResult(bytes / 1024, UnitOfMeasure.Kibibyte),
      ConversionResult(bytes / 1000 / 1000, UnitOfMeasure.Megabyte),
      ConversionResult(bytes / 1024 / 1024, UnitOfMeasure.Mebibyte),
      ConversionResult(bytes / 1000 / 1000 / 1000, UnitOfMeasure.Gigabyte),
      ConversionResult(bytes / 1024 / 1024 / 1024, UnitOfMeasure.Gibibyte),
      ConversionResult(bytes / 1000 / 1000 / 1000 / 1000, UnitOfMeasure.Terabyte),
      ConversionResult(bytes / 1024 / 1024 / 1024 / 1024, UnitOfMeasure.Tebibyte)
    )

  def convert(number: Double, source: UnitOfMeasure): Seq[ConversionResult] =
    source match {
      case UnitOfMeasure.Celsius =>
        Seq(ConversionResult(number * (9.0 / 5) + 32, UnitOfMeasure.Fahrenheit))

      case UnitOfMeasure.Fahrenheit =>
        Seq(ConversionResult((number - 32) * (5 / 9.0), UnitOfMeasure.Celsius))

      case UnitOfMeasure.Kilometer =>
        Seq(ConversionResult(number * 0.62137119, UnitOfMeasure.Mile))

      case UnitOfMeasure.Mile =>
        Seq(ConversionResult(number / 0.62137119, UnitOfMeasure.Kilometer))

      case u @ UnitOfMeasure.Byte =>
        convertFromBytes(number).filter(_.unitOfMeasure != u)

      case u @ UnitOfMeasure.Kilobyte =>
        convertFromBytes((number * 1000).toInt).filter(_.unitOfMeasure != u)

      case u @ UnitOfMeasure.Kibibyte =>
        convertFromBytes((number * 1024).toInt).filter(_.unitOfMeasure != u)

      case u @ UnitOfMeasure.Megabyte =>
        convertFromBytes((number * 1000 * 1000).toInt).filter(_.unitOfMeasure != u)

      case u @ UnitOfMeasure.Mebibyte =>
        convertFromBytes((number * 1024 * 1024).toInt).filter(_.unitOfMeasure != u)

      case u @ UnitOfMeasure.Gigabyte =>
        convertFromBytes((number * 1000 * 1000 * 1000).toInt).filter(_.unitOfMeasure != u)

      case u @ UnitOfMeasure.Gibibyte =>
        convertFromBytes((number * 1024 * 1024 * 1024).toInt).filter(_.unitOfMeasure != u)

      case u @ UnitOfMeasure.Terabyte =>
        convertFromBytes((number * 1000 * 1000 * 1000 * 1000).toInt).filter(_.unitOfMeasure != u)

      case u @ UnitOfMeasure.Tebibyte =>
        convertFromBytes((number * 1024 * 1024 * 1024 * 1024).toInt).filter(_.unitOfMeasure != u)

      case UnitOfMeasure.Pound =>
        Seq(ConversionResult(number * 0.453592, UnitOfMeasure.Kilogram))

      case UnitOfMeasure.Kilogram =>
        Seq(ConversionResult(number / 0.453592, UnitOfMeasure.Pound))

      case UnitOfMeasure.Feet =>
        Seq(
          ConversionResult(number * 12 * 2.54, UnitOfMeasure.Centimeter),
          ConversionResult(number * 12, UnitOfMeasure.Inches)
        )

      case UnitOfMeasure.Inches =>
        Seq(
          ConversionResult(number * 2.54, UnitOfMeasure.Centimeter),
          ConversionResult(number / 12, UnitOfMeasure.Feet)
        )

      case UnitOfMeasure.FeetAndInches =>
        Seq.empty

      case UnitOfMeasure.Centimeter =>
        Seq(
          ConversionResult(number / 2.54, UnitOfMeasure.Inches),
          ConversionResult(number / 2.54 / 12, UnitOfMeasure.Feet),
          ConversionResult(number / 2.54, UnitOfMeasure.FeetAndInches)
        )
    }
}

package commandcenter.command

import cats.data.{Validated, ValidatedNel}
import com.monovore.decline.Argument
import enumeratum.*
import zio.Duration

object CommonArgs {

  implicit val durationArgument: Argument[Duration] = new Argument[Duration] {

    override def read(string: String): ValidatedNel[String, Duration] =
      try Validated.valid(Duration.fromScala(scala.concurrent.duration.Duration(string)))
      catch { case _: IllegalArgumentException => Validated.invalidNel(s"Invalid Duration: $string") }

    override def defaultMetavar: String = "duration"
  }

  def enumArgument[A <: EnumEntry: Enum](varName: String): Argument[A] =
    new Argument[A] {

      override def read(string: String): ValidatedNel[String, A] = {
        val enumEv = implicitly[Enum[A]]

        enumEv.withNameInsensitiveOption(string) match {
          case Some(v) => Validated.valid(v)

          case None =>
            Validated.invalidNel(
              s"Invalid $defaultMetavar. Must be: ${enumEv.values.map(_.entryName).mkString(" | ")}"
            )
        }
      }

      override def defaultMetavar: String = varName
    }
}

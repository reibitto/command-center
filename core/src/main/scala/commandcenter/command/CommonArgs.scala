package commandcenter.command

import cats.data.{ Validated, ValidatedNel }
import com.monovore.decline.Argument
import zio.duration.Duration

object CommonArgs {
  implicit val readDuration: Argument[Duration] = new Argument[Duration] {
    override def read(string: String): ValidatedNel[String, Duration] =
      try {
        Validated.valid(Duration.fromScala(scala.concurrent.duration.Duration(string)))
      } catch { case _: IllegalArgumentException => Validated.invalidNel(s"Invalid Duration: $string") }

    override def defaultMetavar: String = "duration"
  }
}

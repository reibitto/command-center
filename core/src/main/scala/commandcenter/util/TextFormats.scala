package commandcenter.util

import zio.Duration

import java.text.DecimalFormat

object TextFormats {

  implicit class IntExtension(val self: Int) extends AnyVal {

    def withGroupingSeparator: String =
      s"%,d".format(self)
  }

  implicit class DoubleExtension(val self: Double) extends AnyVal {

    def digits(digits: Int): String =
      if (digits <= 0)
        self.toInt.toString
      else
        new DecimalFormat(s"#,###.${"#" * digits}").format(self)

    def percent(digits: Int): String =
      if (self.isNaN)
        "n/a"
      else
        s"${(self * 100).digits(digits)}%"
  }

  implicit class DurationExtension(val self: Duration) extends AnyVal {

    def pretty: String = {
      val minutes = self.toSeconds / 60.0

      if (minutes < 60)
        s"${minutes.digits(1)} minutes"
      else if (minutes < 24 * 60)
        s"${(minutes / 60.0).digits(1)} hours"
      else if (minutes < 24 * 60 * 365.25)
        s"${(minutes / 60.0 / 24.0).digits(0)} days"
      else
        s"${(minutes / 60.0 / 24.0 / 365.25).digits(1)} years"
    }

    def prettyShort: String = {
      val minutes = self.toSeconds / 60.0

      if (minutes < 60)
        s"${minutes.digits(0)} m"
      else if (minutes < 24 * 60)
        s"${(minutes / 60.0).digits(0)} h"
      else if (minutes < 24 * 60 * 365.25)
        s"${(minutes / 60.0 / 24.0).digits(0)} d"
      else
        s"${(minutes / 60.0 / 24.0 / 365.25).digits(1)} y"
    }
  }
}

package commandcenter.util

import java.time.LocalDate
import scala.math.Ordering

object Orderings {

  object LocalDateOrdering extends Ordering[LocalDate] {
    def compare(x: LocalDate, y: LocalDate): Int = x.compareTo(y)
  }
}

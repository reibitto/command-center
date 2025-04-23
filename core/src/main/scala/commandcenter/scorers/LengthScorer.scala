package commandcenter.scorers

import commandcenter.util.StringExtensions.*

object LengthScorer {

  def scoreDefault(target: String, input: String): Double =
    if (target.startsWithIgnoreCase(input))
      1.0 - (target.length - input.length) / target.length.toDouble
    else if (target.contains(input))
      (1.0 - (target.length - input.length) / target.length.toDouble) * 0.5
    else
      0.0

  def scorePrefix(target: String, input: String): Double =
    if (target.startsWithIgnoreCase(input))
      1.0 - (target.length - input.length) / target.length.toDouble
    else
      0.0
}

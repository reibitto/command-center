package commandcenter.scorers

object LengthScorer {
  def score(target: String, input: String): Double =
    if (target.startsWith(input))
      1.0 - (target.length - input.length) / target.length.toDouble
    else if (target.contains(input))
      (1.0 - (target.length - input.length) / target.length.toDouble) * 0.5
    else
      0.0
}

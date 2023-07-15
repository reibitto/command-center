package commandcenter.command

import commandcenter.CommandContext

object Scores {
  val hide: Double = 0
  val low: Double = 1
  val default: Double = 10
  val high: Double = 100
  val veryHigh: Double = 1000

  def low(context: CommandContext): Double = low * context.matchScore
  def default(context: CommandContext): Double = default * context.matchScore
  def high(context: CommandContext): Double = high * context.matchScore
  def veryHigh(context: CommandContext): Double = veryHigh * context.matchScore
}

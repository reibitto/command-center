package commandcenter.command

import commandcenter.CommandContext

object Scores {
  val low: Double     = 1
  val default: Double = 10
  val high: Double    = 100

  def low(context: CommandContext): Double     = low * context.matchScore
  def default(context: CommandContext): Double = default * context.matchScore
  def high(context: CommandContext): Double    = high * context.matchScore
}

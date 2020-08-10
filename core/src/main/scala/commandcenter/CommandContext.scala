package commandcenter

import java.util.Locale

final case class CommandContext(locale: Locale, terminal: CCTerminal, matchScore: Double) {
  def matchScore(score: Double): CommandContext = copy(matchScore = score)
}

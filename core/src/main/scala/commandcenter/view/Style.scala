package commandcenter.view

import java.awt.Color

sealed trait Style

object Style {
  case object Bold extends Style
  case object Underline extends Style
  case object Italic extends Style
  final case class ForegroundColor(value: Color) extends Style
  final case class BackgroundColor(value: Color) extends Style
  final case class FontFamily(value: String) extends Style
  final case class FontSize(value: Int) extends Style
}

final case class StyledText(text: String, styles: Set[Style] = Set.empty)

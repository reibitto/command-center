package commandcenter.ui

import java.awt.Color

// TODO: Add scrollbar colors
final case class CCTheme(
  background: Color,
  foreground: Color,
  black: Color,
  red: Color,
  green: Color,
  yellow: Color,
  blue: Color,
  magenta: Color,
  cyan: Color,
  lightGray: Color,
  darkGray: Color,
  lightRed: Color,
  lightGreen: Color,
  lightYellow: Color,
  lightBlue: Color,
  lightMagenta: Color,
  lightCyan: Color,
  white: Color
) {
  def fromFansiColorCode(colorCode: Int): Option[Color] = colorCode match {
    case 1  => Some(black)
    case 2  => Some(red)
    case 3  => Some(green)
    case 4  => Some(yellow)
    case 5  => Some(blue)
    case 6  => Some(magenta)
    case 7  => Some(cyan)
    case 8  => Some(lightGray)
    case 9  => Some(darkGray)
    case 10 => Some(lightRed)
    case 11 => Some(lightGreen)
    case 12 => Some(lightYellow)
    case 13 => Some(lightBlue)
    case 14 => Some(lightMagenta)
    case 15 => Some(lightCyan)
    case 16 => Some(white)
    case _  => None
  }
}

object CCTheme {
  val default: CCTheme = CCTheme(
    new Color(0x0F111A),
    new Color(198, 198, 198),
    new Color(0x0F111A),
    new Color(236, 91, 57),
    new Color(122, 202, 107),
    new Color(245, 218, 55),
    new Color(66, 142, 255),
    new Color(135, 129, 211),
    new Color(22, 180, 236),
    new Color(100, 100, 100),
    new Color(50, 50, 50),
    new Color(255, 135, 119),
    new Color(165, 222, 153),
    new Color(255, 236, 131),
    new Color(75, 149, 255),
    new Color(135, 129, 211),
    new Color(111, 214, 255),
    new Color(209, 209, 209)
  )
}

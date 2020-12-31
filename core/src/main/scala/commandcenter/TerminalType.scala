package commandcenter

import enumeratum.{ Enum, EnumEntry }

sealed trait TerminalType extends EnumEntry

object TerminalType extends Enum[TerminalType] {
  case object Cli   extends TerminalType
  case object Swing extends TerminalType
  case object Swt   extends TerminalType
  case object Test  extends TerminalType

  lazy val values: IndexedSeq[TerminalType] = findValues
}

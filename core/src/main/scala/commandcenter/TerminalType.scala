package commandcenter

import enumeratum.{ Enum, EnumEntry }

sealed trait TerminalType extends EnumEntry

object TerminalType extends Enum[TerminalType] {
  case object Cli   extends TerminalType
  case object Swing extends TerminalType

  override def values: IndexedSeq[TerminalType] = findValues
}

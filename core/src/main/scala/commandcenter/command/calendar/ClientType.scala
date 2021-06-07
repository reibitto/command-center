package commandcenter.command.calendar

import enumeratum.{ Enum, EnumEntry }

sealed trait ClientType extends EnumEntry

object ClientType extends Enum[ClientType] {
  case object Google extends ClientType

  lazy val values: IndexedSeq[ClientType] = findValues
}

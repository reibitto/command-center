package commandcenter.event

import enumeratum.{Enum, EnumEntry}

sealed trait KeyModifier extends EnumEntry with Product with Serializable

object KeyModifier extends Enum[KeyModifier] {
  case object Shift extends KeyModifier
  case object Control extends KeyModifier
  case object Alt extends KeyModifier
  case object AltGraph extends KeyModifier
  case object Meta extends KeyModifier

  lazy val values: IndexedSeq[KeyModifier] = findValues
}

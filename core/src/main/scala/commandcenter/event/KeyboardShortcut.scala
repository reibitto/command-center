package commandcenter.event

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import io.circe.Decoder

final case class KeyboardShortcut(key: KeyCode, modifiers: Set[KeyModifier]) {
  def isEmpty: Boolean = this == KeyboardShortcut.empty

  def nonEmpty: Boolean = !isEmpty

  override def toString: String = {
    def modifierPart(modifier: KeyModifier): String =
      if (modifiers.contains(modifier)) s"${modifier.entryName} " else ""

    // Done this way to maintain order of modifiers
    s"${KeyModifier.values.map(modifierPart).mkString}$key"
  }
}

object KeyboardShortcut {
  val empty: KeyboardShortcut = KeyboardShortcut(KeyCode.CharUndefined, Set.empty)

  def fromString(shortcut: String): ValidatedNel[String, KeyboardShortcut] =
    shortcut.split("[ +]+") match {
      case Array()        => Invalid(NonEmptyList.one("Keyboard shortcut cannot be empty"))
      case Array(keyName) =>
        KeyCode.withNameInsensitiveOption(keyName) match {
          case Some(keyCode) => Valid(KeyboardShortcut(keyCode, Set.empty))
          case None          => Invalid(NonEmptyList.one(s"$keyName is not a valid key code"))
        }

      case parts =>
        val (errors, keyModifiers) = parts.init
          .map(s => KeyModifier.withNameInsensitiveOption(s).toRight(s"$s not a valid key modifier"))
          .partitionMap(identity)

        if (errors.isEmpty) {
          val keyName = parts.last
          KeyCode.withNameInsensitiveOption(keyName) match {
            case Some(keyCode) => Valid(KeyboardShortcut(keyCode, keyModifiers.toSet))
            case None          => Invalid(NonEmptyList.one(s"$keyName is not a valid key code"))
          }
        } else
          Invalid(NonEmptyList.fromListUnsafe(errors.toList))
    }

  implicit val keyboardShortcutDecoder: Decoder[KeyboardShortcut] = Decoder.decodeString.emap { s =>
    if (s.isEmpty)
      Right(KeyboardShortcut.empty)
    else
      KeyboardShortcut.fromString(s).toEither.left.map(_.toList.mkString("; "))
  }

}

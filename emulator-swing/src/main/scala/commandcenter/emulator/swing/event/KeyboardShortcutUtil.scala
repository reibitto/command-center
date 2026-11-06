package commandcenter.emulator.swing.event

import commandcenter.event.{KeyCode, KeyModifier, KeyboardShortcut}

import java.awt.event.KeyEvent

object KeyboardShortcutUtil {

  def fromKeyEvent(e: KeyEvent): KeyboardShortcut = {
    val modifiers = Set[Option[KeyModifier]](
      Option.when(e.isShiftDown)(KeyModifier.Shift),
      Option.when(e.isControlDown)(KeyModifier.Control),
      Option.when(e.isAltDown)(KeyModifier.Alt),
      Option.when(e.isAltGraphDown)(KeyModifier.AltGraph),
      Option.when(e.isMetaDown)(KeyModifier.Meta)
    ).flatten

    KeyboardShortcut(KeyCode.fromCode(e.getKeyCode), modifiers)
  }
}

package commandcenter.daemon.event

import java.awt.event.{ InputEvent, KeyEvent }

import commandcenter.event.{ KeyCode, KeyModifier, KeyboardShortcut }
import javax.swing.KeyStroke

object KeyboardShortcutUtil {
  def fromKeyEvent(e: KeyEvent): KeyboardShortcut = {
    val modifiers: Set[KeyModifier] = (if (e.isShiftDown) Set(KeyModifier.Shift) else Set.empty) ++
      (if (e.isControlDown) Set(KeyModifier.Control) else Set.empty) ++
      (if (e.isAltDown) Set(KeyModifier.Alt) else Set.empty) ++
      (if (e.isAltGraphDown) Set(KeyModifier.AltGraph) else Set.empty) ++
      (if (e.isMetaDown) Set(KeyModifier.Meta) else Set.empty)

    KeyboardShortcut(KeyCode.fromCode(e.getKeyCode), modifiers)
  }

  def toKeyStroke(shortcut: KeyboardShortcut): KeyStroke = {
    var modifiers: Int = 0

    shortcut.modifiers.foreach {
      case KeyModifier.Shift    => modifiers = modifiers | InputEvent.SHIFT_DOWN_MASK
      case KeyModifier.Control  => modifiers = modifiers | InputEvent.CTRL_DOWN_MASK
      case KeyModifier.Alt      => modifiers = modifiers | InputEvent.ALT_DOWN_MASK
      case KeyModifier.AltGraph => modifiers = modifiers | InputEvent.ALT_GRAPH_DOWN_MASK
      case KeyModifier.Meta     => modifiers = modifiers | InputEvent.META_DOWN_MASK
    }

    KeyStroke.getKeyStroke(shortcut.key.value, modifiers)
  }
}

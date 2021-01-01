package commandcenter.emulator.swt.event

import commandcenter.event.{ KeyCode, KeyModifier, KeyboardShortcut }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.KeyEvent

import java.awt.event.InputEvent
import javax.swing.KeyStroke

object KeyboardShortcutUtil {
  def fromKeyEvent(e: KeyEvent): KeyboardShortcut = {
    val modifiers: Set[KeyModifier] =
      (if ((e.stateMask & SWT.SHIFT) == SWT.SHIFT) Set(KeyModifier.Shift) else Set.empty) ++
        (if ((e.stateMask & SWT.CONTROL) == SWT.CONTROL) Set(KeyModifier.Control) else Set.empty) ++
        (if ((e.stateMask & SWT.ALT) == SWT.ALT) Set(KeyModifier.Alt) else Set.empty) ++
        (if ((e.stateMask & SWT.ALT_GR) == SWT.ALT_GR) Set(KeyModifier.AltGraph) else Set.empty) ++
        (if ((e.stateMask & SWT.COMMAND) == SWT.COMMAND) Set(KeyModifier.Meta) else Set.empty)

    KeyboardShortcut(KeyCode.fromCode(e.keyCode), modifiers)
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

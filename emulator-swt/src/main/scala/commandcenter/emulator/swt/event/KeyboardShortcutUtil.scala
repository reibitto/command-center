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

    KeyboardShortcut(swtToKeyCode(e.keyCode), modifiers)
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

  def swtToKeyCode(swtKeyCode: Int): KeyCode = swtKeyCode match {
    case c if c >= 'a' && c <= 'z' => KeyCode.fromCode(KeyCode.A.value + c - 'a')
    case c if c >= '0' && c <= '9' => KeyCode.fromCode(KeyCode.Num0.value + c - '0')
    case '-'                       => KeyCode.Minus
    case '='                       => KeyCode.Equals
    case '['                       => KeyCode.BraceLeft
    case ']'                       => KeyCode.BraceRight
    case ';'                       => KeyCode.Semicolon
    case '\\'                      => KeyCode.BackSlash
    case '/'                       => KeyCode.Slash
    case '\''                      => KeyCode.Quote
    case ','                       => KeyCode.Comma
    case '.'                       => KeyCode.Period
    case '+'                       => KeyCode.Plus
    case '*'                       => KeyCode.Multiply
    case SWT.ARROW_LEFT            => KeyCode.Left
    case SWT.ARROW_UP              => KeyCode.Up
    case SWT.ARROW_RIGHT           => KeyCode.Right
    case SWT.ARROW_DOWN            => KeyCode.Down
    case SWT.PAGE_UP               => KeyCode.PageUp
    case SWT.PAGE_DOWN             => KeyCode.PageDown
    case SWT.HOME                  => KeyCode.Home
    case SWT.END                   => KeyCode.End
    case SWT.F1                    => KeyCode.F1
    case SWT.F2                    => KeyCode.F2
    case SWT.F3                    => KeyCode.F3
    case SWT.F4                    => KeyCode.F4
    case SWT.F5                    => KeyCode.F5
    case SWT.F6                    => KeyCode.F6
    case SWT.F7                    => KeyCode.F7
    case SWT.F8                    => KeyCode.F8
    case SWT.F9                    => KeyCode.F9
    case SWT.F10                   => KeyCode.F10
    case SWT.F11                   => KeyCode.F11
    case SWT.F12                   => KeyCode.F12
    case SWT.F13                   => KeyCode.F13
    case SWT.F14                   => KeyCode.F14
    case SWT.F15                   => KeyCode.F15
    case SWT.F16                   => KeyCode.F16
    case SWT.F17                   => KeyCode.F17
    case SWT.F18                   => KeyCode.F18
    case SWT.F19                   => KeyCode.F19
    case SWT.F20                   => KeyCode.F20
    case SWT.KEYPAD_MULTIPLY       => KeyCode.Multiply
    case SWT.KEYPAD_ADD            => KeyCode.Add
    case SWT.KEYPAD_SUBTRACT       => KeyCode.Subtract
    case SWT.KEYPAD_DECIMAL        => KeyCode.Decimal
    case SWT.KEYPAD_DIVIDE         => KeyCode.Divide
    case SWT.KEYPAD_0              => KeyCode.Numpad0
    case SWT.KEYPAD_1              => KeyCode.Numpad1
    case SWT.KEYPAD_2              => KeyCode.Numpad2
    case SWT.KEYPAD_3              => KeyCode.Numpad3
    case SWT.KEYPAD_4              => KeyCode.Numpad4
    case SWT.KEYPAD_5              => KeyCode.Numpad5
    case SWT.KEYPAD_6              => KeyCode.Numpad6
    case SWT.KEYPAD_7              => KeyCode.Numpad7
    case SWT.KEYPAD_8              => KeyCode.Numpad8
    case SWT.KEYPAD_9              => KeyCode.Numpad9
    case SWT.KEYPAD_EQUAL          => KeyCode.Equals
    case SWT.KEYPAD_CR             => KeyCode.Enter
    case SWT.HELP                  => KeyCode.Help
    case SWT.NUM_LOCK              => KeyCode.NumLock
    case SWT.CAPS_LOCK             => KeyCode.CapsLock
    case SWT.SCROLL_LOCK           => KeyCode.ScrollLock
    case SWT.PAUSE                 => KeyCode.Pause
    case SWT.PRINT_SCREEN          => KeyCode.PrintScreen
    case SWT.ESC                   => KeyCode.Escape
    case SWT.DEL                   => KeyCode.Delete
    case SWT.TAB                   => KeyCode.Tab
    case SWT.SPACE                 => KeyCode.Space
    case SWT.ALT_GR                => KeyCode.AltGraph
    case SWT.ALT                   => KeyCode.Alt
    case SWT.SHIFT                 => KeyCode.Shift
    case SWT.CTRL                  => KeyCode.Control
    case SWT.COMMAND               => KeyCode.Meta
    case _                         => KeyCode.CharUndefined
  }
}

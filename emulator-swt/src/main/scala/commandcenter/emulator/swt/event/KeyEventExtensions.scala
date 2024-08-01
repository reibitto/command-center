package commandcenter.emulator.swt.event

import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.SWT

object KeyEventExtensions {

  implicit class KeyEventExtension(val self: KeyEvent) extends AnyVal {

    def isAltDown: Boolean =
      (self.stateMask & SWT.ALT) == SWT.ALT

    def isControlDown: Boolean =
      (self.stateMask & SWT.CONTROL) == SWT.CONTROL

    def isShiftDown: Boolean =
      (self.stateMask & SWT.SHIFT) == SWT.SHIFT

    def isMetaDown: Boolean =
      (self.stateMask & SWT.COMMAND) == SWT.COMMAND
  }
}

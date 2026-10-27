package commandcenter.emulator.swing.ui

import commandcenter.CCRuntime.Env
import zio.*

import java.awt.event.{InputMethodEvent, KeyEvent, KeyListener}
import javax.swing.event.{DocumentEvent, DocumentListener}
import javax.swing.JTextField

class ZTextField(implicit runtime: Runtime[Env]) extends JTextField {

  // Only read and written on the EDT
  private var composing: Boolean = false

  def isComposing: Boolean = composing

  override protected def processInputMethodEvent(e: InputMethodEvent): Unit = {
    if (e.getID == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED) {
      val text = e.getText
      val length = if (text == null) 0 else text.getEndIndex - text.getBeginIndex
      composing = length > e.getCommittedCharacterCount
    }

    super.processInputMethodEvent(e)
  }

  def addZKeyListener(keyListener: ZKeyAdapter): Unit =
    addKeyListener(new KeyListener {

      def keyTyped(e: KeyEvent): Unit = Unsafe.unsafe { implicit u =>
        runtime.unsafe.fork(keyListener.keyTyped(e))
      }

      def keyPressed(e: KeyEvent): Unit = Unsafe.unsafe { implicit u =>
        runtime.unsafe.fork(keyListener.keyPressed(e))
      }

      def keyReleased(e: KeyEvent): Unit = Unsafe.unsafe { implicit u =>
        runtime.unsafe.fork(keyListener.keyReleased(e))
      }
    })

  def addOnChangeListener(handler: DocumentEvent => URIO[Env, Unit]): Unit =
    getDocument.addDocumentListener(
      new DocumentListener {

        def onChange(e: DocumentEvent): Unit =
          Unsafe.unsafe { implicit u =>
            runtime.unsafe.run(handler(e))
          }

        override def insertUpdate(e: DocumentEvent): Unit = onChange(e)
        override def removeUpdate(e: DocumentEvent): Unit = onChange(e)
        override def changedUpdate(e: DocumentEvent): Unit = onChange(e)
      }
    )
}

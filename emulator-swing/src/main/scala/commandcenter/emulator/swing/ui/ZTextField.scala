package commandcenter.emulator.swing.ui

import java.awt.event.{ KeyEvent, KeyListener }

import commandcenter.CCRuntime.Env
import javax.swing.JTextField
import javax.swing.event.{ DocumentEvent, DocumentListener }
import zio.{ Runtime, URIO }

class ZTextField(implicit runtime: Runtime[Env]) extends JTextField {
  def addZKeyListener(keyListener: ZKeyAdapter): Unit =
    addKeyListener(new KeyListener {
      def keyTyped(e: KeyEvent): Unit    = runtime.unsafeRunAsync_(keyListener.keyTyped(e))
      def keyPressed(e: KeyEvent): Unit  = runtime.unsafeRunAsync_(keyListener.keyPressed(e))
      def keyReleased(e: KeyEvent): Unit = runtime.unsafeRunAsync_(keyListener.keyReleased(e))
    })

  def addOnChangeListener(handler: DocumentEvent => URIO[Env, Unit]): Unit =
    getDocument.addDocumentListener(
      new DocumentListener {
        def onChange(e: DocumentEvent): Unit = runtime.unsafeRunAsync_(handler(e))

        override def insertUpdate(e: DocumentEvent): Unit  = onChange(e)
        override def removeUpdate(e: DocumentEvent): Unit  = onChange(e)
        override def changedUpdate(e: DocumentEvent): Unit = onChange(e)
      }
    )
}

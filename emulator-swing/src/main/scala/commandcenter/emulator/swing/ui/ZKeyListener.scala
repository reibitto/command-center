package commandcenter.emulator.swing.ui

import java.awt.event.KeyEvent

import commandcenter.CCRuntime.Env
import zio.{ URIO, ZIO }

class ZKeyAdapter {
  def keyTyped(e: KeyEvent): URIO[Env, Unit] = ZIO.unit

  def keyPressed(e: KeyEvent): URIO[Env, Unit] = ZIO.unit

  def keyReleased(e: KeyEvent): URIO[Env, Unit] = ZIO.unit
}

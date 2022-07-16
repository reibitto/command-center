package commandcenter.emulator.swing.ui

import commandcenter.CCRuntime.Env
import zio.{URIO, ZIO}

import java.awt.event.KeyEvent

class ZKeyAdapter {
  def keyTyped(e: KeyEvent): URIO[Env, Unit] = ZIO.unit

  def keyPressed(e: KeyEvent): URIO[Env, Unit] = ZIO.unit

  def keyReleased(e: KeyEvent): URIO[Env, Unit] = ZIO.unit
}

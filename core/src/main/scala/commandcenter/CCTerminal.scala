package commandcenter

import java.awt.Dimension
import commandcenter.CCRuntime.Env
import zio.{ RIO, URIO }

trait CCTerminal {
  def terminalType: TerminalType

  def opacity: RIO[Env, Float]
  def setOpacity(opacity: Float): RIO[Env, Unit]
  def isOpacitySupported: URIO[Env, Boolean]

  def size: RIO[Env, Dimension]
  def setSize(width: Int, height: Int): RIO[Env, Unit]

  def reload: RIO[Env, Unit]
}

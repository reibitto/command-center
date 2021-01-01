package commandcenter

import java.awt.Dimension
import commandcenter.CCRuntime.Env
import zio.{ RIO, UIO, URIO, ZIO }

object TestTerminal extends CCTerminal {
  def terminalType: TerminalType = TerminalType.Test

  def opacity: RIO[Env, Float] = UIO(1.0f)

  def setOpacity(opacity: Float): RIO[Env, Unit] = ZIO.unit

  def isOpacitySupported: URIO[Env, Boolean] = UIO(false)

  def size: RIO[Env, Dimension] = UIO(new Dimension(80, 40))

  def setSize(width: Int, height: Int): RIO[Env, Unit] = ZIO.unit

  def reload: RIO[Env, Unit] = ZIO.unit
}

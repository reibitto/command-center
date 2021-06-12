package commandcenter

import commandcenter.CCRuntime.Env
import commandcenter.command.PreviewResult
import zio.{ Chunk, RIO, UIO, URIO, ZIO }

import java.awt.Dimension

object TestTerminal extends CCTerminal {
  def terminalType: TerminalType = TerminalType.Test

  def opacity: RIO[Env, Float] = UIO(1.0f)

  def setOpacity(opacity: Float): RIO[Env, Unit] = ZIO.unit

  def isOpacitySupported: URIO[Env, Boolean] = UIO(false)

  def size: RIO[Env, Dimension] = UIO(new Dimension(80, 40))

  def setSize(width: Int, height: Int): RIO[Env, Unit] = ZIO.unit

  def reload: RIO[Env, Unit] = ZIO.unit

  def showMore[A](
    moreResults: Chunk[PreviewResult[A]],
    previewSource: PreviewResult[A],
    pageSize: Int
  ): RIO[Env, Unit] = ZIO.unit
}

package commandcenter.tools

import zio.prelude.Reader
import zio.prelude.fx.ZPure
import zio.{ Has, RIO, Task, ZIO }

trait Tools {
  def processId: Long
  def activate: Task[Unit]
  def hide: Task[Unit]
  def setClipboard(text: String): Task[Unit]
}

object Tools {
  def processId: Reader[Has[Tools], Long] =
    ZPure.access[Has[Tools]](_.get.processId)

  def activate: RIO[Has[Tools], Unit] =
    ZIO.serviceWith[Tools](_.activate)

  def hide: RIO[Has[Tools], Unit] =
    ZIO.serviceWith[Tools](_.hide)

  def setClipboard(text: String): RIO[Has[Tools], Unit] =
    ZIO.serviceWith[Tools](_.setClipboard(text))
}

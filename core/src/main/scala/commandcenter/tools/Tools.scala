package commandcenter.tools

import zio.{RIO, Task, URIO, ZIO}

import java.io.InputStream

trait Tools {
  def processId: Long
  def activate: Task[Unit]
  def hide: Task[Unit]
  def setClipboard(text: String): Task[Unit]
  def beep: Task[Unit]
  def playSound(inputStream: InputStream): Task[Unit]
}

object Tools {

  def processId: URIO[Tools, Long] =
    ZIO.serviceWith[Tools](_.processId)

  def activate: RIO[Tools, Unit] =
    ZIO.serviceWithZIO[Tools](_.activate)

  def hide: RIO[Tools, Unit] =
    ZIO.serviceWithZIO[Tools](_.hide)

  def setClipboard(text: String): RIO[Tools, Unit] =
    ZIO.serviceWithZIO[Tools](_.setClipboard(text))

  def beep: RIO[Tools, Unit] =
    ZIO.serviceWithZIO[Tools](_.beep)

  def playSound(inputStream: InputStream): RIO[Tools, Unit] =
    ZIO.serviceWithZIO[Tools](_.playSound(inputStream))
}

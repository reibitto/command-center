package commandcenter.cli.zmq

import org.zeromq.ZMQ
import zio.{ RIO, Task, TaskManaged, ZManaged }
import zio.blocking.{ effectBlocking, Blocking }

final case class ZSubscriber(socket: ZMQ.Socket) {
  def connect(address: String): TaskManaged[ZMQ.Socket] =
    ZManaged.fromAutoCloseable(Task(socket.connect(address)).as(socket))

  def subscribeAll: Task[Boolean] =
    Task(socket.subscribe(Array.emptyByteArray))

  def receiveString: RIO[Blocking, String] =
    effectBlocking(socket.recvStr())
}

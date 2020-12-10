package commandcenter.cli.zmq

import org.zeromq.ZMQ
import zio.{ Task, TaskManaged, ZManaged }

final case class ZPublisher(socket: ZMQ.Socket) {
  def bind(address: String): TaskManaged[ZMQ.Socket] =
    ZManaged.fromAutoCloseable(Task(socket.bind(address)).as(socket))

  def send(s: String): Task[Boolean] =
    Task(socket.send(s))
}

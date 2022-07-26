package commandcenter

import sttp.capabilities.zio.ZioStreams
import sttp.client3.{Identity, RequestT, Response, SttpBackend}
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.{Task, ZIO, ZLayer}

trait Sttp {
  def send[T](request: RequestT[Identity, T, ZioStreams]): Task[Response[T]]
}

object Sttp {

  def send[T](request: RequestT[Identity, T, ZioStreams]): ZIO[Sttp, Throwable, Response[T]] =
    ZIO.serviceWithZIO[Sttp](_.send(request))
}

final case class SttpLive(backend: SttpBackend[Task, ZioStreams]) extends Sttp {

  def send[T](request: RequestT[Identity, T, ZioStreams]): Task[Response[T]] = {
    request.send(backend)
  }
}

object SttpLive {

  def make: ZLayer[Any, Throwable, Sttp] = {
    ZLayer {
      for {
        backend <- HttpClientZioBackend()
      } yield SttpLive(backend)
    }

  }
}

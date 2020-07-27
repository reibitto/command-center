package commandcenter.util

import zio.clock.Clock
import zio.duration._
import zio._

object Debounced {
  def apply[R, E, A](waitTime: Duration): UIO[ZIO[R, E, A] => URIO[R with Clock, Fiber[E, A]]] =
    RefM.make(Option.empty[Fiber[E, A]]).map { previousRef => (f: ZIO[R, E, A]) =>
      previousRef.modify {
        case Some(previous) => previous.interrupt *> f.delay(waitTime).fork.map(a => (a, Some(a)))
        case None           => f.delay(waitTime).fork.map(a => (a, Some(a)))
      }
    }
}

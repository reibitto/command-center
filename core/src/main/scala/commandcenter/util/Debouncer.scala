package commandcenter.util

import zio.{ Fiber, IO, Promise, RefM, UIO, URIO, ZIO }
import zio.clock.Clock
import zio.duration.Duration

final case class DebounceState[E, A](
  fiber: Fiber[E, A],
  delay: Promise[Nothing, Unit],
  completion: Promise[Nothing, Unit],
  triggered: Boolean
)

final case class Debouncer[R, E, A](
  debounceFn: ZIO[R, E, A] => URIO[R with Clock, Fiber[E, A]],
  stateRef: RefM[Option[DebounceState[E, A]]]
) {
  def apply(zio: ZIO[R, E, A]): URIO[R with Clock, Fiber[E, A]] = debounceFn(zio)

  def triggerNow: IO[E, Option[Promise[Nothing, Unit]]] =
    stateRef.modify {
      case Some(state) =>
        state.delay.succeed(()).as {
          (Some(state.completion), Some(state.copy(triggered = true)))
        }

      case None => UIO((None, None))
    }

  def triggerNowAwait: IO[E, Unit] =
    for {
      promiseOpt <- triggerNow
      _          <- ZIO.foreach_(promiseOpt) { promise =>
                      promise.await
                    }
    } yield ()

}

object Debouncer {
  def make[R, E, A](waitTime: Duration): UIO[Debouncer[R, E, A]] =
    for {
      ref <- RefM.make(Option.empty[DebounceState[E, A]])
      fn   = (f: ZIO[R, E, A]) =>
               ref.modify { state =>
                 for {
                   promise     <- Promise.make[Nothing, Unit]
                   completionP <- Promise.make[Nothing, Unit]
                   _           <- ZIO.foreach_(state) { s =>
                                    s.fiber.interrupt.unless(s.triggered)
                                  }
                   result      <- (promise.await.timeout(waitTime) *> f.tap(_ => completionP.succeed(()))).fork.map { fb =>
                                    (fb, Some(DebounceState(fb, promise, completionP, triggered = false)))
                                  }
                 } yield result
               }
    } yield Debouncer(fn, ref)
}

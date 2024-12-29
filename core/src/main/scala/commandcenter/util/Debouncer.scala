package commandcenter.util

import zio.*

final case class DebounceState[E, A](
    running: Fiber[E, A],
    delay: Promise[Nothing, Unit],
    completion: Promise[Nothing, Unit],
    triggered: Boolean
)

final case class Debouncer[R, E, A](
    debounceFn: ZIO[R, E, A] => URIO[R, Fiber[E, A]],
    stateRef: Ref.Synchronized[Option[DebounceState[E, A]]]
) {
  def apply(zio: ZIO[R, E, A]): URIO[R, Fiber[E, A]] = debounceFn(zio)

  /** Interrupt the in-progress search immediately (if it exists) */
  def interruptSearch: UIO[Unit] =
    stateRef.getAndUpdateZIO { stateOpt =>
      ZIO
        .foreachDiscard(stateOpt) { state =>
          state.running.interrupt
        }
        .as(stateOpt)
    }.unit

  def triggerNow: IO[E, Option[Promise[Nothing, Unit]]] =
    stateRef.modifyZIO {
      case Some(state) =>
        state.delay.succeed(()).as {
          (Some(state.completion), Some(state.copy(triggered = true)))
        }

      case None => ZIO.succeed((None, None))
    }

  def triggerNowAwait: IO[E, Unit] =
    for {
      promiseOpt <- triggerNow
      _ <- ZIO.foreachDiscard(promiseOpt) { promise =>
             promise.await
           }
    } yield ()

}

object Debouncer {

  def make[R, E, A](waitTime: Duration, opTimeout: Option[Duration]): UIO[Debouncer[R, E, A]] =
    for {
      ref <- Ref.Synchronized.make(Option.empty[DebounceState[E, A]])
      opFn = (op: ZIO[R, E, A]) =>
               ref.modifyZIO { state =>
                 for {
                   delayPromise      <- Promise.make[Nothing, Unit]
                   completionPromise <- Promise.make[Nothing, Unit]
                   _ <- ZIO.foreachDiscard(state) { s =>
                          s.running.interruptFork.unless(s.triggered)
                        }
                   opFiber <- (for {
                                _ <- delayPromise.await.timeout(waitTime)
                                result <- opTimeout match {
                                            case Some(timeout) =>
                                              op.timeout(timeout).flatMap {
                                                case Some(a) => ZIO.succeed(a)
                                                case None =>
                                                  val errorMessage = "Operation in debouncer timed out"
                                                  ZIO.logWarning(errorMessage) *> ZIO.dieMessage(errorMessage)
                                              }
                                            case None => op
                                          }
                                _ <- completionPromise.succeed(())
                              } yield result).forkDaemon
                   updatedDebounceState = DebounceState(opFiber, delayPromise, completionPromise, triggered = false)
                 } yield (opFiber, Some(updatedDebounceState))
               }
    } yield Debouncer(opFn, ref)
}

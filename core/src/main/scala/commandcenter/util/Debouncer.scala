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
    stateRef.updateZIO(stateOpt => ZIO.foreachDiscard(stateOpt)(_.running.interrupt).as(stateOpt))

  def triggerNow: IO[E, Option[Promise[Nothing, Unit]]] =
    stateRef.modifyZIO {
      case Some(state) =>
        state.delay.succeed(()).as {
          (Some(state.completion), Some(state.copy(triggered = true)))
        }

      case None => ZIO.succeed((None, None))
    }

  def triggerNowAwait: IO[E, Unit] =
    triggerNow.flatMap(ZIO.foreachDiscard(_)(_.await))

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
                   _                 <- ZIO.foreachDiscard(state) { s =>
                          s.running.interruptFork.unless(s.triggered)
                        }
                   opFiber <- (delayPromise.await.timeout(waitTime) *> opTimeout.fold(op) { timeout =>
                                val errorMessage = "Operation in debouncer timed out"
                                op.timeout(timeout)
                                  .someOrElseZIO(ZIO.logWarning(errorMessage) *> ZIO.dieMessage(errorMessage))
                              }).forkDaemon
                   _ <- (opFiber.await *> completionPromise.succeed(())).forkDaemon
                   updatedDebounceState = DebounceState(opFiber, delayPromise, completionPromise, triggered = false)
                 } yield (opFiber, Some(updatedDebounceState))
               }
    } yield Debouncer(opFn, ref)
}

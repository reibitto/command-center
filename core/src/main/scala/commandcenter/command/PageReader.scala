package commandcenter.command

import commandcenter.CCRuntime.Env
import zio.*
import zio.stream.ZStream

/** Reads a stream page by page, keeping it open in between so each page
  * continues where the previous one stopped. The stream closes with the scope
  * passed to `make`.
  */
final class PageReader[A] private (
    pull: ZIO[Env, Option[CommandError], Chunk[PreviewResult[A]]],
    stateRef: Ref[PageReader.State[A]],
    lock: Semaphore
) {

  /** Returns up to `pageSize` results and whether more remain. Knowing that
    * takes reading one result ahead, which is kept for the next page.
    */
  def nextPage(pageSize: Int): ZIO[Env, CommandError, (Chunk[PreviewResult[A]], Boolean)] =
    lock.withPermit {
      for {
        state  <- stateRef.get
        filled <- fill(state, pageSize + 1)
        (page, rest) = filled.buffer.splitAt(pageSize)
        _ <- stateRef.set(filled.copy(buffer = rest))
      } yield (page, rest.nonEmpty)
    }

  private def fill(state: PageReader.State[A], size: Int): ZIO[Env, CommandError, PageReader.State[A]] =
    if (state.done || state.buffer.length >= size) ZIO.succeed(state)
    else
      pull
        // A pull that ended, failed or was interrupted can't be resumed
        .onError(_ => stateRef.set(state.copy(done = true)))
        .foldZIO(
          {
            case None        => ZIO.succeed(state.copy(done = true))
            case Some(error) => ZIO.fail(error)
          },
          chunk => fill(state.copy(buffer = state.buffer ++ chunk), size)
        )
}

object PageReader {

  final private case class State[A](buffer: Chunk[PreviewResult[A]], done: Boolean)

  def make[A](results: ZStream[Env, CommandError, PreviewResult[A]]): URIO[Env & Scope, PageReader[A]] =
    for {
      pull     <- results.toPull
      stateRef <- Ref.make(State[A](Chunk.empty, done = false))
      lock     <- Semaphore.make(1)
      // Added after `toPull`, so it runs before the stream's finalizers: waits for an in-flight page, then stops reads
      _ <- ZIO.addFinalizer(lock.withPermit(stateRef.set(State[A](Chunk.empty, done = true))))
    } yield new PageReader(pull, stateRef, lock)
}

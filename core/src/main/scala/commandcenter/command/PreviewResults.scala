package commandcenter.command

import zio.Chunk
import zio.stream.ZStream

sealed trait PreviewResults[+R]

object PreviewResults {
  def one[R](result: PreviewResult[R]): PreviewResults[R] =
    PreviewResults.Single(result)

  def fromIterable[R](results: Iterable[PreviewResult[R]]): PreviewResults[R] =
    PreviewResults.Multiple(Chunk.fromIterable(results))

  def paginated[A](stream: ZStream[Any, CommandError, PreviewResult[A]], pageSize: Int): PreviewResults[A] =
    PreviewResults.Paginated(stream, pageSize)

  final case class Single[R](result: PreviewResult[R]) extends PreviewResults[R]

  final case class Multiple[R](results: Chunk[PreviewResult[R]]) extends PreviewResults[R]

  final case class Paginated[R](results: ZStream[Any, CommandError, PreviewResult[R]], pageSize: Int)
      extends PreviewResults[R]
}

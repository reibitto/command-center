package commandcenter.command

import commandcenter.CCRuntime.Env
import zio.*

sealed trait MoreResults

object MoreResults {
  case object Exhausted extends MoreResults

  final case class Remaining[A](
      reader: PageReader[A],
      initialPageSize: Int,
      morePageSize: Int,
      totalRemaining: Option[Long]
  ) extends MoreResults {

    def moreMessage: String =
      totalRemaining match {
        case Some(remaining) => s"Load $morePageSize of $remaining more..."
        case None            => "More..."
      }

    def nextPage: ZIO[Env, CommandError, (Chunk[PreviewResult[A]], Option[Remaining[A]])] =
      reader.nextPage(morePageSize).map { case (page, hasMore) =>
        (page, Option.when(hasMore)(copy(totalRemaining = totalRemaining.map(_ - page.length))))
      }
  }
}

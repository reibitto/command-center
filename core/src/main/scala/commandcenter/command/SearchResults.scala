package commandcenter.command

import commandcenter.view.Rendered
import zio.*

final case class SearchResults[A](
    searchTerm: String,
    previews: Chunk[PreviewResult[A]],
    errors: Chunk[CommandError] = Chunk.empty
) {
  lazy val rendered: Chunk[Rendered] = previews.map(_.renderFn())

  def hasChange(otherSearchTerm: String): Boolean =
    searchTerm.trim != otherSearchTerm.trim
}

object SearchResults {
  def empty[A]: SearchResults[A] = SearchResults("", Chunk.empty)
}

package commandcenter.command

import commandcenter.view.Rendered
import zio.Chunk

final case class SearchResults[R](
  searchTerm: String,
  previews: Chunk[PreviewResult[R]],
  errors: Chunk[CommandError] = Chunk.empty
) {
  lazy val rendered: Chunk[Rendered] = previews.map(_.renderFn())

  def hasChange(otherSearchTerm: String): Boolean =
    searchTerm.trim != otherSearchTerm.trim
}

object SearchResults {
  def empty[R]: SearchResults[R] = SearchResults("", Chunk.empty)
}

package commandcenter.command

import commandcenter.view.Rendered

final case class SearchResults[R](searchTerm: String, results: Vector[PreviewResult[R]]) {
  lazy val rendered: Vector[Rendered] = results.map(_.renderFn())

  def hasChange(otherSearchTerm: String): Boolean =
    searchTerm.trim != otherSearchTerm.trim
}

object SearchResults {
  def empty[R]: SearchResults[R] = SearchResults("", Vector.empty)
}

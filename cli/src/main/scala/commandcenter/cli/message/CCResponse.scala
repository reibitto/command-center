package commandcenter.cli.message

import commandcenter.command.SearchResults
import commandcenter.view.Rendered
import io.circe._

sealed trait CCResponse

object CCResponse {
  final case class Search(searchTerm: String, results: Vector[SearchResult]) extends CCResponse

  object Search {
    implicit val encoder: Encoder[Search] = Encoder.forProduct2("searchTerm", "results")(s => (s.searchTerm, s.results))

    def fromSearchResults[A](results: SearchResults[A]): Search =
      Search(
        results.searchTerm,
        results.previews.map { r =>
          r.renderFn() match {
            case ansi: Rendered.Ansi => Some(SearchResult(r.source.title, ansi, r.score))
            case _: Rendered.Styled  => None
          }
        }.collect { case Some(r) => r }
      )
  }

  final case class SearchResult(title: String, rendered: Rendered.Ansi, score: Double)

  object SearchResult {
    implicit val encoder: Encoder[SearchResult] =
      Encoder.forProduct3("title", "rendered", "score")(s => (s.title, s.rendered.ansiStr.render, s.score))
  }

  final case class Run(success: Boolean) extends CCResponse

  object Run {
    implicit val encoder: Encoder[Run] = Encoder.forProduct1("success")(s => s.success)
  }

  case object Exit extends CCResponse {
    implicit val encoder: Encoder[Exit.type] = Encoder.encodeUnit.contramap(_ => ())
  }

  final case class Error(message: String) extends CCResponse

  object Error {
    implicit val encoder: Encoder[Error] = Encoder.forProduct1("message")(s => s.message)
  }

  implicit val encoder: Encoder[CCResponse] = Encoder.instance {
    case a: Search    => Search.encoder(a)
    case a: Run       => Run.encoder(a)
    case a: Exit.type => Exit.encoder(a)
    case a: Error     => Error.encoder(a)
  }
}

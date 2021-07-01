package commandcenter.command

sealed trait MoreResults
object MoreResults {
  case object Exhausted                                                 extends MoreResults
  final case class Remaining[A](paginated: PreviewResults.Paginated[A]) extends MoreResults
}

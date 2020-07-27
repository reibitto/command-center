package commandcenter.ui

sealed trait EventResult

object EventResult {
  case object Success                                    extends EventResult
  case object Exit                                       extends EventResult
  final case class UnexpectedError(throwable: Throwable) extends EventResult
}

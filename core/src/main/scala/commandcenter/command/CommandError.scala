package commandcenter.command

import com.monovore.decline.Help

sealed trait CommandError

object CommandError {
  final case class UnexpectedException(throwable: Throwable) extends CommandError
  final case class InternalError(message: String)            extends CommandError
  final case class CliError(help: Help)                      extends CommandError
  case object NotApplicable                                  extends CommandError
}

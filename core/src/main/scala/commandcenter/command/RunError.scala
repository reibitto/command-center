package commandcenter.command

import com.monovore.decline.Help

sealed abstract class RunError(cause: Throwable) extends Exception(cause) with Product with Serializable

object RunError {
  final case class UnexpectedException(cause: Throwable) extends RunError(cause)
  final case class InternalError(message: String)        extends RunError(null)
  final case class CliError(help: Help)                  extends RunError(null)
}

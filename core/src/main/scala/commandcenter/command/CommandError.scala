package commandcenter.command

import com.monovore.decline.Help

sealed trait CommandError {
  def toThrowable: Throwable
}

object CommandError {
  final case class UnexpectedException(throwable: Throwable) extends CommandError {
    def toThrowable: Throwable = throwable
  }

  final case class InternalError(message: String) extends CommandError {
    def toThrowable: Throwable = new Exception(message)
  }

  final case class CliError(help: Help) extends CommandError {
    def toThrowable: Throwable = new Exception(help.toString())
  }

  case object NotApplicable extends CommandError {
    override def toThrowable: Throwable = new Exception("Not applicable")
  }
}

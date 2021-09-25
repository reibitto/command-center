package commandcenter.command

import com.monovore.decline.Help
import commandcenter.view.Rendered

sealed trait CommandError {
  def toThrowable: Throwable
}

object CommandError {
  final case class ShowMessage(rendered: Rendered, score: Double) extends CommandError {
    def previewResult: PreviewResult[Nothing] = PreviewResult.nothing(rendered).score(score)

    override def toThrowable: Throwable = new Exception(s"Show message: $rendered")
  }

  final case class UnexpectedException(throwable: Throwable)      extends CommandError {
    def toThrowable: Throwable = throwable
  }

  final case class InternalError(message: String)                 extends CommandError {
    def toThrowable: Throwable = new Exception(message)
  }

  final case class CliError(help: Help)                           extends CommandError {
    def toThrowable: Throwable = new Exception(help.toString())
  }

  case object NotApplicable                                       extends CommandError {
    override def toThrowable: Throwable = new Exception("Not applicable")
  }
}

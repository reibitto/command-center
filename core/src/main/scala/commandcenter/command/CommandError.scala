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

  final case class UnexpectedError[A](throwable: Throwable, source: Command[A]) extends CommandError {
    def toThrowable: Throwable = throwable
  }

  object UnexpectedError {

    def apply[A](source: Command[A])(throwable: Throwable): UnexpectedError[A] =
      UnexpectedError(throwable, source)

    def fromMessage[A](source: Command[A])(message: String): UnexpectedError[A] =
      UnexpectedError(new Exception(message), source)
  }

  final case class CliError(help: Help) extends CommandError {
    def toThrowable: Throwable = new Exception(help.toString())
  }

  case object NotApplicable extends CommandError {
    override def toThrowable: Throwable = new Exception("Not applicable")
  }

}

package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.command.util.CalculatorUtil
import io.circe.Decoder
import zio.ZIO

case class CalculatorCommand() extends Command[Double] {
  val commandType: CommandType = CommandType.CalculatorCommand

  val commandNames: List[String] = List.empty

  val title: String = "Calculator"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Double]]] =
    for {
      evaluatedValue <-
        ZIO.fromOption(CalculatorUtil.evaluate(searchInput.input)).orElseFail(CommandError.NotApplicable)
    } yield List(
      Preview(evaluatedValue)
        .onRun(searchInput.context.ccProcess.setClipboard(evaluatedValue.toString))
        .score(Scores.high(searchInput.context))
    )
}

object CalculatorCommand extends CommandPlugin[CalculatorCommand] {
  implicit val decoder: Decoder[CalculatorCommand] = Decoder.const(CalculatorCommand())
}

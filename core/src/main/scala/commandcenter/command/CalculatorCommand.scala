package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.util.CalculatorUtil
import commandcenter.tools
import zio.{ TaskManaged, ZIO, ZManaged }

final case class CalculatorCommand() extends Command[Double] {
  val commandType: CommandType = CommandType.CalculatorCommand

  val commandNames: List[String] = List.empty

  val title: String = "Calculator"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Double]]] =
    for {
      evaluatedValue <-
        ZIO.fromOption(CalculatorUtil.evaluate(searchInput.input)).orElseFail(CommandError.NotApplicable)
    } yield List(
      Preview(evaluatedValue)
        .onRun(tools.setClipboard(evaluatedValue.toString))
        .score(Scores.high(searchInput.context))
    )
}

object CalculatorCommand extends CommandPlugin[CalculatorCommand] {
  def make(config: Config): TaskManaged[CalculatorCommand] = ZManaged.succeed(CalculatorCommand())
}

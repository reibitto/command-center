package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommandError._
import commandcenter.tools
import commandcenter.view.DefaultView
import zio.{ UManaged, ZIO, ZManaged }

import scala.util.matching.Regex

final case class TemperatureCommand() extends Command[Double] {
  val commandType: CommandType = CommandType.TemperatureCommand

  val commandNames: List[String] = List.empty

  val title: String = "Temperature"

  val temperatureRegex: Regex = """(-?\d+\.?\d*)\s*([cCＣfFＦ度どド])""".r

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Double]] =
    for {
      (temperature, unit) <- ZIO.fromOption {
                               temperatureRegex.unapplySeq(searchInput.input.trim).flatMap {
                                 case List(value, sourceUnit) =>
                                   val v = value.toDouble

                                   val targetUnit = if (sourceUnit.equalsIgnoreCase("f")) "C" else "F"

                                   val temp =
                                     if (sourceUnit.equalsIgnoreCase("f"))
                                       (v - 32) * (5 / 9.0)
                                     else
                                       v * (9.0 / 5) + 32

                                   Some((temp, targetUnit))

                                 case _ => None
                               }
                             }.orElseFail(NotApplicable)
      temperatureFormatted = f"$temperature%.1f $unit"
    } yield PreviewResults.one(
      Preview(temperature)
        .score(Scores.high(searchInput.context))
        .view(DefaultView(title, temperatureFormatted))
        .onRun(tools.setClipboard(temperatureFormatted))
    )
}

object TemperatureCommand extends CommandPlugin[TemperatureCommand] {
  def make(config: Config): UManaged[TemperatureCommand] = ZManaged.succeed(TemperatureCommand())
}

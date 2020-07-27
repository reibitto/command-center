package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.command.CommandError._
import commandcenter.util.ProcessUtil
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO

import scala.util.matching.Regex

final case class TemperatureCommand() extends Command[Double] {
  val commandType: CommandType = CommandType.TemperatureCommand

  val commandNames: List[String] = List.empty

  val title: String = "Temperature"

  val temperatureRegex: Regex = """(-?\d+\.?\d*)\s*([cCＣfFＦ度どド])""".r

  // TODO: Add Temperature value class and format to 1 decimal place
  override def inputPreview(
    input: String,
    context: CommandContext
  ): ZIO[Env, CommandError, List[PreviewResult[Double]]] =
    for {
      (temperature, unit) <- ZIO.fromOption {
                              temperatureRegex.unapplySeq(input.trim).map {
                                case List(value, sourceUnit) =>
                                  val v = value.toDouble

                                  val targetUnit = if (sourceUnit.equalsIgnoreCase("f")) "C" else "F"

                                  val temp = if (sourceUnit.equalsIgnoreCase("f")) {
                                    (v - 32) * (5 / 9.0)
                                  } else {
                                    v * (9.0 / 5) + 32
                                  }

                                  (temp, targetUnit)
                              }
                            }.orElseFail(NotApplicable)
      temperatureFormatted = f"$temperature%.1f $unit"
    } yield List(
      Preview(temperature)
        .score(Scores.high(context))
        .view(DefaultView(title, temperatureFormatted))
        .onRun(ProcessUtil.copyToClipboard(temperatureFormatted))
    )
}

object TemperatureCommand extends CommandPlugin[TemperatureCommand] {
  implicit val decoder: Decoder[TemperatureCommand] = Decoder.const(TemperatureCommand())
}

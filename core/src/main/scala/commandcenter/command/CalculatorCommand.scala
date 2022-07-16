package commandcenter.command

import cats.data.Validated
import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.command.util.CalculatorUtil
import commandcenter.command.CalculatorCommand.{FunctionsList, Parameters, ParametersList}
import commandcenter.tools.Tools
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import io.circe.Decoder
import zio.{Managed, ZIO}

import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale

final case class CalculatorCommand(parameters: Parameters) extends Command[BigDecimal] {
  val commandType: CommandType = CommandType.CalculatorCommand

  val commandNames: List[String] = List("calculator")

  val title: String = "Calculator"

  private val helpTypeOpt = Opts.argument[String]("functions or parameters").mapValidated {
    case arg if arg.matches("functions?")  => Validated.valid(FunctionsList)
    case arg if arg.matches("parameters?") => Validated.valid(ParametersList)
    case arg                               => Validated.invalidNel(s"$arg is not valid: should be 'functions' or 'parameters'.")
  }

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[BigDecimal]] =
    previewHelp(searchInput) <> previewEvaluation(searchInput)

  private def previewHelp(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[BigDecimal]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed = decline.Command(title, title)(helpTypeOpt).parse(input.args)
      (helpTitle, message) <- ZIO
                                .fromEither(parsed)
                                .fold(
                                  help => (title, HelpMessage.formatted(help)),
                                  helpType => (helpType.title, CalculatorCommand.helpMessageByType(helpType))
                                )
    } yield PreviewResults.one(
      Preview(BigDecimal(0.0))
        .score(Scores.high(searchInput.context))
        .rendered(Renderer.renderDefault(helpTitle, message))
    )

  private def previewEvaluation(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[BigDecimal]] =
    for {
      evaluatedValue <- ZIO
                          .fromOption(new CalculatorUtil(parameters).evaluate(searchInput.input))
                          .orElseFail(CommandError.NotApplicable)
    } yield PreviewResults.one(
      Preview(evaluatedValue)
        .renderedAnsi(parameters.format(evaluatedValue))
        .onRun(Tools.setClipboard(parameters.format(evaluatedValue)))
        .score(Scores.high(searchInput.context))
    )
}

object CalculatorCommand extends CommandPlugin[CalculatorCommand] {

  def make(config: Config): Managed[CommandPluginError, CalculatorCommand] =
    ZIO.fromEither(config.as[CalculatorCommand]).mapError(CommandPluginError.UnexpectedException).toManaged_

  implicit val decoder: Decoder[CalculatorCommand] = Decoder.instance { c =>
    val decimalFormat = new DecimalFormat()
    val decimalFormatSymbols = decimalFormat.getDecimalFormatSymbols
    for {
      decimalSeparator      <- c.get[Option[Char]]("decimalSeparator")
      groupingSeparator     <- c.get[Option[Char]]("groupingSeparator")
      parameterSeparator    <- c.get[Option[Char]]("parameterSeparator")
      groupingSize          <- c.get[Option[Int]]("groupingSize")
      groupingUsed          <- c.get[Option[Boolean]]("groupingUsed")
      maximumFractionDigits <- c.get[Option[Int]]("maximumFractionDigits")
    } yield CalculatorCommand(
      Parameters(
        decimalSeparator.getOrElse(decimalFormatSymbols.getDecimalSeparator),
        groupingSeparator.getOrElse(decimalFormatSymbols.getGroupingSeparator),
        parameterSeparator.getOrElse(';'),
        groupingSize.getOrElse(decimalFormat.getGroupingSize),
        groupingUsed.getOrElse(decimalFormat.isGroupingUsed),
        maximumFractionDigits.getOrElse(decimalFormat.getMaximumFractionDigits)
      )
    )
  }

  def helpMessageByType(helpType: HelpType): fansi.Str =
    helpType match {
      case FunctionsList  => CalculatorUtil.helpMessageFunctionsList
      case ParametersList => CalculatorUtil.helpMessageParametersList
    }

  sealed trait HelpType extends Product {
    val title: String
  }

  case object FunctionsList extends HelpType {
    override val title: String = "List of all operators/functions"
  }

  case object ParametersList extends HelpType {
    override val title: String = "List of all configuration parameters"
  }

  final case class Parameters(
      decimalSeparator: Char,
      groupingSeparator: Char,
      parameterSeparator: Char,
      groupingSize: Int,
      groupingUsed: Boolean,
      maximumFractionDigits: Int
  ) {

    def decimalFormat: DecimalFormat =
      new DecimalFormat() {

        setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.getDefault) {
          setDecimalSeparator(decimalSeparator)
          setGroupingSeparator(groupingSeparator)
        })
        setGroupingSize(groupingSize)
        setGroupingUsed(groupingUsed)
        setMaximumFractionDigits(maximumFractionDigits)
        setParseBigDecimal(true)
      }

    def format(value: BigDecimal): String = {
      val format = decimalFormat
      if (value.isWhole) format.setMaximumFractionDigits(0)
      format.format(value.bigDecimal)
    }
  }
}

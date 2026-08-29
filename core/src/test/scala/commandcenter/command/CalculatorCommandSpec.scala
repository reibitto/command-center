package commandcenter.command

import commandcenter.command.CalculatorCommand.{FunctionsList, Parameters, ParametersList}
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import commandcenter.CommandBaseSpec
import zio.*
import zio.test.*

object CalculatorCommandSpec extends CommandBaseSpec {

  // Fixed explicitly (rather than derived from the host's default Locale) so the suite is deterministic regardless
  // of where it runs.
  private val defaultParameters: Parameters =
    Parameters(
      decimalSeparator = '.',
      groupingSeparator = ',',
      parameterSeparator = ';',
      groupingSize = 3,
      groupingUsed = true,
      maximumFractionDigits = 10
    )

  private val command: CalculatorCommand = CalculatorCommand(defaultParameters)

  private def preview(cmd: CalculatorCommand, input: String): ZIO[Env, CommandError, PreviewResults[BigDecimal]] =
    cmd.preview(SearchInput(input, List(input), cmd.commandNames, defaultCommandContext))

  private def singleResult(results: PreviewResults[BigDecimal]): PreviewResult.Some[BigDecimal] =
    results match {
      case PreviewResults.Single(r) => r.asInstanceOf[PreviewResult.Some[BigDecimal]]
      case other                    => throw new AssertionError(s"Expected a single preview result, got: $other")
    }

  private def plainText(result: PreviewResult.Some[BigDecimal]): String =
    result.renderFn() match {
      case Rendered.Ansi(s) => s.plainText
      case other            => throw new AssertionError(s"Expected an Ansi-rendered preview, got: $other")
    }

  /** The raw (unformatted) evaluated value for an expression, using the given
    * command/parameters.
    */
  private def valueOf(cmd: CalculatorCommand, input: String): ZIO[Env, CommandError, BigDecimal] =
    preview(cmd, input).map(r => singleResult(r).result)

  private def value(input: String): ZIO[Env, CommandError, BigDecimal] = valueOf(command, input)

  /** The rendered/formatted text for an expression, using the given
    * command/parameters.
    */
  private def textOf(cmd: CalculatorCommand, input: String): ZIO[Env, CommandError, String] =
    preview(cmd, input).map(r => plainText(singleResult(r)))

  private def text(input: String): ZIO[Env, CommandError, String] = textOf(command, input)

  private def isNotApplicable(cmd: CalculatorCommand, input: String): ZIO[Env, Nothing, Boolean] =
    preview(cmd, input).exit.map(_.isFailure)

  private def notApplicable(input: String): ZIO[Env, Nothing, Boolean] = isNotApplicable(command, input)

  private def approxEqual(a: BigDecimal, b: BigDecimal, tolerance: BigDecimal = BigDecimal("1e-9")): Boolean =
    (a - b).abs < tolerance

  def spec: Spec[TestEnvironment & Env, Any] =
    suite("CalculatorCommandSpec")(
      suite("basic arithmetic")(
        test("adds") {
          value("2+3").map(v => assertTrue(v == BigDecimal(5)))
        },
        test("subtracts") {
          value("10-4").map(v => assertTrue(v == BigDecimal(6)))
        },
        test("multiplies") {
          value("6*7").map(v => assertTrue(v == BigDecimal(42)))
        },
        test("divides") {
          value("10/4").map(v => assertTrue(v == BigDecimal(2.5)))
        },
        test("modulo") {
          value("10%3").map(v => assertTrue(v == BigDecimal(1)))
        },
        test("respects operator precedence (* before +)") {
          value("2+3*4").map(v => assertTrue(v == BigDecimal(14)))
        },
        test("respects parentheses") {
          value("(2+3)*4").map(v => assertTrue(v == BigDecimal(20)))
        },
        test("respects nested parentheses") {
          value("((1+2)*(3+4))").map(v => assertTrue(v == BigDecimal(21)))
        },
        test("evaluates same-precedence operators left-to-right") {
          value("20-5-5").map(v => assertTrue(v == BigDecimal(10)))
        },
        test("handles unary minus") {
          value("-5+10").map(v => assertTrue(v == BigDecimal(5)))
        },
        test("handles unary plus") {
          value("+5").map(v => assertTrue(v == BigDecimal(5)))
        },
        test("repeated unary signs are not supported by the grammar") {
          notApplicable("--5").map(assertTrue(_))
        },
        test("power") {
          value("2^10").map(v => assertTrue(v == BigDecimal(1024)))
        },
        test("power with a fractional exponent") {
          value("2^0.5").map(v => assertTrue(approxEqual(v, BigDecimal(math.sqrt(2)))))
        },
        test("** is an alias for ^") {
          value("2**10").map(v => assertTrue(v == BigDecimal(1024)))
        },
        test("** binds tighter than multiplication, like ^") {
          value("2*3**2").map(v => assertTrue(v == BigDecimal(18)))
        },
        test("integer division truncates toward zero") {
          value("7//2").map(v => assertTrue(v == BigDecimal(3)))
        },
        test("integer division with a negative dividend truncates toward zero") {
          value("-7//2").map(v => assertTrue(v == BigDecimal(-3)))
        },
        test("integer division with a negative divisor truncates toward zero") {
          value("7//(-2)").map(v => assertTrue(v == BigDecimal(-3)))
        },
        test("integer division shares precedence with * / %") {
          value("2+7//2").map(v => assertTrue(v == BigDecimal(5)))
        },
        test("integer division by zero does not produce a result") {
          notApplicable("7//0").map(assertTrue(_))
        },
        test("division by zero does not produce a result") {
          notApplicable("10/0").map(assertTrue(_))
        },
        test("garbage input does not produce a result") {
          notApplicable("this is not math").map(assertTrue(_))
        },
        test("unbalanced parentheses do not produce a result") {
          notApplicable("(1+2").map(assertTrue(_))
        },
        test("a trailing operator does not produce a result") {
          notApplicable("1+").map(assertTrue(_))
        }
      ),
      suite("functions")(
        test("sqrt of a perfect square") {
          value("sqrt16").map(v => assertTrue(v == BigDecimal(4)))
        },
        test("sqrt of a non-perfect square") {
          value("sqrt2").map(v => assertTrue(approxEqual(v, BigDecimal(math.sqrt(2)))))
        },
        test("sqrt of a negative number does not produce a result") {
          notApplicable("sqrt(-1)").map(assertTrue(_))
        },
        test("ceil rounds up") {
          value("ceil4.1").map(v => assertTrue(v == BigDecimal(5)))
        },
        test("ceil on a negative number rounds toward zero") {
          value("ceil(-4.1)").map(v => assertTrue(v == BigDecimal(-4)))
        },
        test("floor rounds down") {
          value("floor4.9").map(v => assertTrue(v == BigDecimal(4)))
        },
        test("floor on a negative number rounds away from zero") {
          value("floor(-4.1)").map(v => assertTrue(v == BigDecimal(-5)))
        },
        test("round rounds half up") {
          value("round4.5").map(v => assertTrue(v == BigDecimal(5)))
        },
        test("round rounds down below the midpoint") {
          value("round4.4").map(v => assertTrue(v == BigDecimal(4)))
        },
        test("round on a negative half rounds away from zero") {
          value("round(-4.5)").map(v => assertTrue(v == BigDecimal(-5)))
        },
        test("factorial") {
          value("5!").map(v => assertTrue(v == BigDecimal(120)))
        },
        test("factorial of zero") {
          value("0!").map(v => assertTrue(v == BigDecimal(1)))
        },
        test("infix choose (binomial coefficient)") {
          value("5choose2").map(v => assertTrue(v == BigDecimal(10)))
        },
        test("prefixed choose") {
          value("choose(5;2)").map(v => assertTrue(v == BigDecimal(10)))
        },
        test("choose rejects r > n") {
          notApplicable("choose(2;5)").map(assertTrue(_))
        },
        test("gcd") {
          value("gcd(12;18)").map(v => assertTrue(v == BigDecimal(6)))
        },
        test("max is variadic") {
          value("max(3;7;2)").map(v => assertTrue(v == BigDecimal(7)))
        },
        test("min is variadic") {
          value("min(3;7;2)").map(v => assertTrue(v == BigDecimal(2)))
        },
        test("hypot") {
          value("hypot(3;4)").map(v => assertTrue(v == BigDecimal(5)))
        },
        test("atan2") {
          value("atan2(1;1)").map(v => assertTrue(approxEqual(v, BigDecimal(math.Pi) / 4)))
        },
        test("log with an explicit base") {
          value("log(2;8)").map(v => assertTrue(approxEqual(v, BigDecimal(3))))
        },
        test("log2") {
          value("log2(8)").map(v => assertTrue(approxEqual(v, BigDecimal(3))))
        },
        test("log2 of 1 is 0") {
          value("log2(1)").map(v => assertTrue(v == BigDecimal(0)))
        },
        test("abs of a negative number") {
          value("abs(-5.5)").map(v => assertTrue(v == BigDecimal(5.5)))
        },
        test("abs of a positive number is unchanged") {
          value("abs5.5").map(v => assertTrue(v == BigDecimal(5.5)))
        },
        test("ln of 1 is 0") {
          value("ln1").map(v => assertTrue(v == BigDecimal(0)))
        },
        test("ln of 0 does not produce a result") {
          notApplicable("ln0").map(assertTrue(_))
        },
        test("exp of 0 is 1") {
          value("exp0").map(v => assertTrue(v == BigDecimal(1)))
        },
        test("exp of 1 is e") {
          value("exp1").map(v => assertTrue(approxEqual(v, BigDecimal(math.E))))
        },
        test("sin of 0 is 0") {
          value("sin0").map(v => assertTrue(v == BigDecimal(0)))
        },
        test("cos of 0 is 1") {
          value("cos0").map(v => assertTrue(v == BigDecimal(1)))
        },
        test("tan of 0 is 0") {
          value("tan0").map(v => assertTrue(v == BigDecimal(0)))
        },
        test("asin of 1 is pi/2") {
          value("asin1").map(v => assertTrue(approxEqual(v, BigDecimal(math.Pi) / 2)))
        },
        test("acos of 1 is 0") {
          value("acos1").map(v => assertTrue(v == BigDecimal(0)))
        },
        test("sinh of 0 is 0") {
          value("sinh0").map(v => assertTrue(v == BigDecimal(0)))
        },
        test("cosh of 0 is 1") {
          value("cosh0").map(v => assertTrue(v == BigDecimal(1)))
        },
        test("tanh of 0 is 0") {
          value("tanh0").map(v => assertTrue(v == BigDecimal(0)))
        },
        test("pi constant") {
          value("pi").map(v => assertTrue(approxEqual(v, BigDecimal(math.Pi))))
        },
        test("e constant") {
          value("e").map(v => assertTrue(approxEqual(v, BigDecimal(math.E))))
        },
        test("e constant combines with arithmetic") {
          value("2*e").map(v => assertTrue(approxEqual(v, BigDecimal(math.E) * 2)))
        },
        test("e constant does not shadow the exp function") {
          value("exp1").map(v => assertTrue(approxEqual(v, BigDecimal(math.E))))
        },
        test("ln of e is 1") {
          value("ln(e)").map(v => assertTrue(approxEqual(v, BigDecimal(1))))
        },
        test("toDeg converts pi radians to 180 degrees") {
          value("toDeg(pi)").map(v => assertTrue(approxEqual(v, BigDecimal(180))))
        },
        test("toRad converts 180 degrees to pi radians") {
          value("toRad(180)").map(v => assertTrue(approxEqual(v, BigDecimal(math.Pi))))
        },
        test("functions compose with arithmetic") {
          value("sqrt(4)+3*2").map(v => assertTrue(v == BigDecimal(8)))
        }
      ),
      suite("random")(
        test("random() is within [0, 1)") {
          ZIO
            .foreach(1 to 25)(_ => value("random"))
            .map(vs => assertTrue(vs.forall(v => v >= BigDecimal(0) && v < BigDecimal(1))))
        },
        test("random(a;b) is within [a, b)") {
          ZIO
            .foreach(1 to 25)(_ => value("random(5;10)"))
            .map(vs => assertTrue(vs.forall(v => v >= BigDecimal(5) && v < BigDecimal(10))))
        },
        test("random int(a;b) is a whole number within [a, b]") {
          ZIO
            .foreach(1 to 25)(_ => value("random int(1;6)"))
            .map(vs => assertTrue(vs.forall(v => v.isWhole && v >= BigDecimal(1) && v <= BigDecimal(6))))
        }
      ),
      suite("locale-specific parameter configurations")(
        test("German-style separators: ',' decimal, '.' grouping, ';' parameters") {
          val germanCommand = CalculatorCommand(
            defaultParameters.copy(decimalSeparator = ',', groupingSeparator = '.', parameterSeparator = ';')
          )

          for {
            parsed    <- valueOf(germanCommand, "1.234,5+0,5")
            formatted <- textOf(germanCommand, "1000*1000")
            fn        <- valueOf(germanCommand, "gcd(12;18)")
          } yield assertTrue(parsed == BigDecimal(1235), formatted == "1.000.000", fn == BigDecimal(6))
        },
        test("French-style separators: ',' decimal, ' ' grouping") {
          val frenchCommand = CalculatorCommand(
            defaultParameters.copy(decimalSeparator = ',', groupingSeparator = ' ', parameterSeparator = ';')
          )

          for {
            parsed    <- valueOf(frenchCommand, "1 234,5")
            formatted <- textOf(frenchCommand, "1000*1000")
          } yield assertTrue(parsed == BigDecimal(1234.5), formatted == "1 000 000")
        },
        test("a custom parameter separator is honored") {
          val customCommand = CalculatorCommand(defaultParameters.copy(parameterSeparator = ':'))

          for {
            withColon          <- valueOf(customCommand, "gcd(12:18)")
            withSemicolonFails <- isNotApplicable(customCommand, "gcd(12;18)")
          } yield assertTrue(withColon == BigDecimal(6), withSemicolonFails)
        },
        test("groupingUsed = false omits the grouping separator when rendering") {
          val noGroupingCommand = CalculatorCommand(defaultParameters.copy(groupingUsed = false))

          textOf(noGroupingCommand, "1000000+1").map(t => assertTrue(t == "1000001"))
        },
        test("groupingUsed = true adds the grouping separator when rendering") {
          textOf(command, "1000000+1").map(t => assertTrue(t == "1,000,001"))
        },
        test("a custom grouping size is honored") {
          val groupOfFourCommand = CalculatorCommand(defaultParameters.copy(groupingSize = 4))

          textOf(groupOfFourCommand, "12345678").map(t => assertTrue(t == "1234,5678"))
        },
        test("maximumFractionDigits truncates/rounds the rendered output") {
          val twoDigitsCommand = CalculatorCommand(defaultParameters.copy(maximumFractionDigits = 2))

          textOf(twoDigitsCommand, "10/3").map(t => assertTrue(t == "3.33"))
        },
        test("whole-number results render without a decimal point regardless of maximumFractionDigits") {
          textOf(command, "6/3").map(t => assertTrue(t == "2"))
        }
      ),
      suite("help")(
        test("'calculator functions' lists the supported operators/functions") {
          textOf(command, "calculator functions").map(t =>
            assertTrue(t.contains("atan2"), t.contains("gcd"), t.contains("abs"), t.contains("log2"), t.contains("e"))
          )
        },
        test("'calculator parameters' lists the supported configuration parameters") {
          textOf(command, "calculator parameters").map(t =>
            assertTrue(t.contains("decimalSeparator"), t.contains("groupingSeparator"))
          )
        },
        test("an unrecognized help topic falls back to the command's usage text") {
          preview(command, "calculator nonsense").exit.map(exit => assertTrue(exit.isSuccess))
        },
        test("'calculator' with no argument falls back to the command's usage text") {
          preview(command, "calculator").exit.map(exit => assertTrue(exit.isSuccess))
        }
      ),
      suite("integration with Command.search")(
        test("matches a plain math expression") {
          for {
            results <- Command.search(Vector(command), Map.empty, "2+2", defaultCommandContext)
          } yield assertTrue(results.previews.nonEmpty)
        },
        test("does not match plain text") {
          for {
            results <- Command.search(Vector(command), Map.empty, "hello world", defaultCommandContext)
          } yield assertTrue(results.previews.isEmpty)
        }
      )
    )
}

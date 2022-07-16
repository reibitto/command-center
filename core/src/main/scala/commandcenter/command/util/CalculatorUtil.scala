package commandcenter.command.util

import commandcenter.command.CalculatorCommand.Parameters
import fastparse.*
import fastparse.Parsed.Success
import fastparse.ScalaWhitespace.*
import spire.random.{Random, Uniform}
import spire.std.any.{BigDecimalAlgebra, BigDecimalIsTrig}

import scala.annotation.tailrec

/*
 * Operator precedence (from highest to lowest):
 * <ul>
 *   <li>()</li>
 *   <li>functions</li>
 *   <li>&#94; (power)</li>
 *   <li>* / %</li>
 *   <li>unary + -</li>
 *   <li>+ - *</li>
 * </ul>
 */
final class CalculatorUtil(parameters: Parameters) {
  import CalculatorUtil.*

  private val randomGenerator = Random.initGenerator

  def evaluate(input: String): Option[BigDecimal] =
    parse(input, expression(_)) match {
      case Success(value, _) => Some(value)
      case _                 => None
    }

  private def expression[Dummy: P]: P[BigDecimal] = P(addSub ~ End)

  private def addSub[Dummy: P]: P[BigDecimal] =
    P(unaryPlusMinus ~ (CharIn("+\\-").! ~/ unaryPlusMinus).rep).map(evaluateOperatorSequence)

  private def unaryPlusMinus[Dummy: P]: P[BigDecimal] =
    P(CharIn("+\\-").!.? ~ mulDivMod).map {
      case (Some("-"), value) => -value
      case (_, value)         => value
    }

  private def mulDivMod[Dummy: P]: P[BigDecimal] =
    P(power ~ (CharIn("*/%").! ~/ power).rep).map(evaluateOperatorSequence)

  // FIXME evaluating over double, because with pow(BigDecimal, BigDecimal) spire can go into infinite loop
  private def power[Dummy: P]: P[BigDecimal] =
    P((function ~ ("^" ~/ function).?).map {
      case (base, Some(exp)) => math.pow(base.doubleValue, exp.doubleValue)
      case (base, _)         => base
    })

  private def function[Dummy: P]: P[BigDecimal] =
    P(
      startsWithTerm | acos | asin | atan2 | atan | ceil | choosePrefixed | cosh | cos | exp | floor | gcd | hypot |
        ln | log | max | min | random | round | sinh | sin | sqrt | tanh | tan | toDeg | toRad
    )

  private def startsWithTerm[Dummy: P]: P[BigDecimal] =
    P(for {
      value1 <- term
      value2 <- P("choose" ~/ term)
                  .filter(d => isWholeNonNegative(value1) && isWholeNonNegative(d) && value1 >= d)
                  .map(binomial(value1, _)) |
                  P("!").filter(_ => isWholeNonNegative(value1)).map(_ => factorial(value1)) |
                  P(Pass).map(_ => value1)
    } yield value2)

  private def acos[Dummy: P]: P[BigDecimal] = P("acos" ~/ term).map(spire.math.acos(_))

  private def asin[Dummy: P]: P[BigDecimal] = P("asin" ~/ term).map(spire.math.asin(_))

  private def atan[Dummy: P]: P[BigDecimal] = P("atan" ~/ term).map(spire.math.atan(_))

  private def atan2[Dummy: P]: P[BigDecimal] =
    P("atan2" ~/ multipleParametersParser(2)).map { case Seq(y, x) =>
      spire.math.atan2(y, x)
    }

  private def ceil[Dummy: P]: P[BigDecimal] = P("ceil" ~/ term).map(spire.math.ceil)

  private def choosePrefixed[Dummy: P]: P[BigDecimal] =
    P("choose" ~/ multipleParametersParser(2)).filter { case Seq(n, r) =>
      isWholeNonNegative(n) && isWholeNonNegative(r) && n >= r
    }.map { case Seq(n, r) =>
      binomial(n, r)
    }

  private def cos[Dummy: P]: P[BigDecimal] = P("cos" ~/ term).map(spire.math.cos(_))

  private def cosh[Dummy: P]: P[BigDecimal] = P("cosh" ~/ term).map(spire.math.cosh(_))

  private def exp[Dummy: P]: P[BigDecimal] = P("exp" ~/ term).map(spire.math.exp)

  private def floor[Dummy: P]: P[BigDecimal] = P("floor" ~/ term).map(spire.math.floor)

  private def gcd[Dummy: P]: P[BigDecimal] =
    P("gcd" ~/ multipleParametersParser(2)).filter { case Seq(a, b) =>
      a.isWhole && b.isWhole
    }.map { case Seq(a, b) =>
      BigDecimal(a.toBigInt.gcd(b.toBigInt))
    }

  private def hypot[Dummy: P]: P[BigDecimal] =
    P("hypot" ~/ multipleParametersParser(2)).map { case Seq(x, y) =>
      spire.math.hypot(x, y)
    }

  // FIXME evaluating over double, because with log(BigDecimal) spire can go into infinite loop
  private def ln[Dummy: P]: P[BigDecimal] = P("ln" ~/ term).map(d => math.log(d.doubleValue))

  private def log[Dummy: P]: P[BigDecimal] =
    P("log" ~/ multipleParametersParser(2)).map { case Seq(base, number) =>
      math.log(number.doubleValue) / math.log(base.doubleValue)
    }

  private def max[Dummy: P]: P[BigDecimal] = P("max" ~/ multipleParametersParser(1)).map(_.max)

  private def min[Dummy: P]: P[BigDecimal] = P("min" ~/ multipleParametersParser(1)).map(_.min)

  private def random[Dummy: P]: P[BigDecimal] =
    P("random" ~ "int".!.? ~ multipleParametersParser(2)).map {
      case (Some(_), Seq(a, b)) => BigDecimal(Uniform(a.toBigInt, b.toBigInt).apply(randomGenerator))
      case (_, Seq(a, b))       => Uniform(a, b).apply(randomGenerator)
      case _                    => BigDecimal(0) // TODO: Handle this case properly
    } |
      P("random").map(_ => Uniform(BigDecimal(0.0), BigDecimal(1.0)).apply(randomGenerator))

  private def round[Dummy: P]: P[BigDecimal] = P("round" ~/ term).map(spire.math.round)

  private def sin[Dummy: P]: P[BigDecimal] = P("sin" ~/ term).map(spire.math.sin(_))

  private def sinh[Dummy: P]: P[BigDecimal] = P("sinh" ~/ term).map(spire.math.sinh(_))

  private def sqrt[Dummy: P]: P[BigDecimal] = P("sqrt" ~/ term).map(spire.math.sqrt(_))

  private def tan[Dummy: P]: P[BigDecimal] = P("tan" ~/ term).map(spire.math.tan(_))

  private def tanh[Dummy: P]: P[BigDecimal] = P("tanh" ~/ term).map(spire.math.tanh(_))

  private def toDeg[Dummy: P]: P[BigDecimal] = P("toDeg" ~/ term).map(_ / spire.math.pi * 180.0)

  private def toRad[Dummy: P]: P[BigDecimal] = P("toRad" ~/ term).map(_ * spire.math.pi / 180.0)

  private def term[Dummy: P]: P[BigDecimal] = P(number | const | NoCut(parens))

  private def number[Dummy: P]: P[BigDecimal] =
    P(
      (CharIn("0-9") | parameters.groupingSeparator.toString).repX(
        1
      ) ~~ (parameters.decimalSeparator.toString ~~ CharIn("0-9").repX).?
    ).!.map(parameters.decimalFormat.parse(_) match {
      case number: java.math.BigDecimal => BigDecimal(number)
    })

  private def const[Dummy: P]: P[BigDecimal] = P(IgnoreCase("pi").map(_ => spire.math.pi))

  private def parens[Dummy: P]: P[BigDecimal] = P("(" ~/ addSub ~ ")")

  private def multipleParametersParser[Dummy: P](count: Int): P[Seq[BigDecimal]] =
    P(term.rep(count) ~ !parameters.parameterSeparator.toString) |
      P(term.rep(count, sep = parameters.parameterSeparator.toString)) |
      P("(" ~ addSub.rep(count, sep = parameters.parameterSeparator.toString) ~ ")")
}

object CalculatorUtil {

  def helpMessageFunctionsList: String =
    List(
      "",
      "+ - * / % ^",
      "!, choose",
      "acos, asin, atan, atan2, cos, sin, tan, cosh, sinh, tanh, toDeg, toRad",
      "exp, log, ln, sqrt, hypot",
      "ceil, floor, round, gcd, max, min",
      "random"
    ).mkString("\n")

  def helpMessageParametersList: String =
    List(
      "",
      "decimalSeparator:      Char    (e.g. \",\")",
      "groupingSeparator:     Char    (e.g. \"_\")",
      "parameterSeparator:    Char    (e.g. \";\")",
      "groupingSize:          Int     (e.g. 3)",
      "groupingUsed:          Boolean (e.g. true)",
      "maximumFractionDigits: Int     (e.g. 10)"
    ).mkString("\n")

  private def evaluateOperatorSequence(tree: (BigDecimal, Seq[(String, BigDecimal)])): BigDecimal = {
    val (base, ops) = tree
    ops.foldLeft(base) { case (left, (op, right)) =>
      op match {
        case "+" => left + right
        case "-" => left - right
        case "*" => left * right
        case "/" => left / right
        case "%" => left % right
      }
    }
  }

  private def isWholeNonNegative(b: BigDecimal) = b.isWhole && b >= 0

  private def binomial(n: BigDecimal, r: BigDecimal): BigDecimal = {
    @tailrec
    def binomialRec(n: BigInt, r: BigInt, i: BigInt, acc: BigInt): BigInt =
      if (i >= r) acc
      else binomialRec(n, r, i + 1, acc * (n - i) / (i + 1))

    val r2 = if (2 * r <= n) r else n - r
    BigDecimal(binomialRec(n.toBigInt, r2.toBigInt, 0, 1))
  }

  private def factorial(b: BigDecimal): BigDecimal = {
    @tailrec
    def factorialRec(b: BigInt, acc: BigInt): BigInt = if (b <= 1) acc else factorialRec(b - 1, b * acc)

    BigDecimal(factorialRec(b.toBigInt, 1))
  }
}

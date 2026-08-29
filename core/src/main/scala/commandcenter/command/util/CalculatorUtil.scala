package commandcenter.command.util

import commandcenter.command.CalculatorCommand.Parameters
import fastparse.*
import fastparse.MultiLineWhitespace.*
import fastparse.Parsed.Success

import java.math.{MathContext, RoundingMode}
import scala.annotation.tailrec
import scala.util.control.NonFatal

/*
 * Operator precedence (from highest to lowest):
 * <ul>
 *   <li>()</li>
 *   <li>functions</li>
 *   <li>&#94; ** (power)</li>
 *   <li>* / // %</li>
 *   <li>unary + -</li>
 *   <li>+ - *</li>
 * </ul>
 */
final class CalculatorUtil(parameters: Parameters) {
  import CalculatorUtil.*

  private val rng = new scala.util.Random()

  def evaluate(input: String): Option[BigDecimal] =
    try
      parse(input, expression(_)) match {
        case Success(value, _) => Some(value)
        case _                 => None
      }
    catch {
      case NonFatal(_) => None
    }

  private def expression[T: P]: P[BigDecimal] = P(addSub ~ End)

  private def addSub[T: P]: P[BigDecimal] =
    P(unaryPlusMinus ~ (CharIn("+\\-").! ~/ unaryPlusMinus).rep).map(evaluateOperatorSequence)

  private def unaryPlusMinus[T: P]: P[BigDecimal] =
    P(CharIn("+\\-").!.? ~ mulDivMod).map {
      case (Some("-"), value) => -value
      case (_, value)         => value
    }

  private def mulDivMod[T: P]: P[BigDecimal] =
    P(power ~ (("//" | "*" | "/" | "%").! ~/ power).rep).map(evaluateOperatorSequence)

  private def power[T: P]: P[BigDecimal] =
    P((function ~ (("**" | "^") ~/ function).?).map {
      case (base, Some(exp)) => BigDecimal(math.pow(base.doubleValue, exp.doubleValue))
      case (base, _)         => base
    })

  private def function[T: P]: P[BigDecimal] =
    P(
      startsWithTerm | abs | acos | asin | atan2 | atan | ceil | choosePrefixed | cosh | cos | exp | floor | gcd |
        hypot | ln | log2 | log | max | min | random | round | sinh | sin | sqrt | tanh | tan | toDeg | toRad
    )

  private def abs[T: P]: P[BigDecimal] = P("abs" ~/ term).map(_.abs)

  private def startsWithTerm[T: P]: P[BigDecimal] =
    P(for {
      value1 <- term
      value2 <- P("choose" ~/ term)
                  .filter(d => isWholeNonNegative(value1) && isWholeNonNegative(d) && value1 >= d)
                  .map(binomial(value1, _)) |
                  P("!").filter(_ => isWholeNonNegative(value1)).map(_ => factorial(value1)) |
                  P(Pass).map(_ => value1)
    } yield value2)

  private def acos[T: P]: P[BigDecimal] = P("acos" ~/ term).map(viaDouble(math.acos))

  private def asin[T: P]: P[BigDecimal] = P("asin" ~/ term).map(viaDouble(math.asin))

  private def atan[T: P]: P[BigDecimal] = P("atan" ~/ term).map(viaDouble(math.atan))

  private def atan2[T: P]: P[BigDecimal] =
    P("atan2" ~/ multipleParametersParser(2)).map { case Seq(y, x) =>
      BigDecimal(math.atan2(y.doubleValue, x.doubleValue))
    }

  private def ceil[T: P]: P[BigDecimal] = P("ceil" ~/ term).map(bigDecimalCeil)

  private def choosePrefixed[T: P]: P[BigDecimal] =
    P("choose" ~/ multipleParametersParser(2)).filter { case Seq(n, r) =>
      isWholeNonNegative(n) && isWholeNonNegative(r) && n >= r
    }.map { case Seq(n, r) =>
      binomial(n, r)
    }

  private def cos[T: P]: P[BigDecimal] = P("cos" ~/ term).map(viaDouble(math.cos))

  private def cosh[T: P]: P[BigDecimal] = P("cosh" ~/ term).map(viaDouble(math.cosh))

  private def exp[T: P]: P[BigDecimal] = P("exp" ~/ term).map(viaDouble(math.exp))

  private def floor[T: P]: P[BigDecimal] = P("floor" ~/ term).map(bigDecimalFloor)

  private def gcd[T: P]: P[BigDecimal] =
    P("gcd" ~/ multipleParametersParser(2)).filter { case Seq(a, b) =>
      a.isWhole && b.isWhole
    }.map { case Seq(a, b) =>
      BigDecimal(a.toBigInt.gcd(b.toBigInt))
    }

  private def hypot[T: P]: P[BigDecimal] =
    P("hypot" ~/ multipleParametersParser(2)).map { case Seq(x, y) =>
      bigDecimalSqrt(x * x + y * y)
    }

  private def ln[T: P]: P[BigDecimal] = P("ln" ~/ term).map(viaDouble(math.log))

  private def log2[T: P]: P[BigDecimal] = P("log2" ~/ term).map(viaDouble(d => math.log(d) / math.log(2)))

  private def log[T: P]: P[BigDecimal] =
    P("log" ~/ multipleParametersParser(2)).map { case Seq(base, number) =>
      BigDecimal(math.log(number.doubleValue) / math.log(base.doubleValue))
    }

  private def max[T: P]: P[BigDecimal] = P("max" ~/ multipleParametersParser(1)).map(_.max)

  private def min[T: P]: P[BigDecimal] = P("min" ~/ multipleParametersParser(1)).map(_.min)

  private def random[T: P]: P[BigDecimal] =
    P("random" ~ "int".!.? ~ multipleParametersParser(2)).map {
      case (Some(_), Seq(a, b)) => BigDecimal(randomBigInt(a.toBigInt, b.toBigInt, rng))
      case (_, Seq(a, b))       => a + BigDecimal(rng.nextDouble()) * (b - a)
      case _                    => BigDecimal(0) // unreachable: multipleParametersParser(2) always yields 2 elements
    } |
      P("random").map(_ => BigDecimal(rng.nextDouble()))

  private def round[T: P]: P[BigDecimal] = P("round" ~/ term).map(bigDecimalRound)

  private def sin[T: P]: P[BigDecimal] = P("sin" ~/ term).map(viaDouble(math.sin))

  private def sinh[T: P]: P[BigDecimal] = P("sinh" ~/ term).map(viaDouble(math.sinh))

  private def sqrt[T: P]: P[BigDecimal] = P("sqrt" ~/ term).map(bigDecimalSqrt)

  private def tan[T: P]: P[BigDecimal] = P("tan" ~/ term).map(viaDouble(math.tan))

  private def tanh[T: P]: P[BigDecimal] = P("tanh" ~/ term).map(viaDouble(math.tanh))

  private def toDeg[T: P]: P[BigDecimal] = P("toDeg" ~/ term).map(_ / Pi * 180)

  private def toRad[T: P]: P[BigDecimal] = P("toRad" ~/ term).map(_ * Pi / 180)

  private def term[T: P]: P[BigDecimal] = P(number | const | NoCut(parens))

  private def number[T: P]: P[BigDecimal] =
    P(
      (CharIn("0-9") | parameters.groupingSeparator.toString).repX(
        1
      ) ~~ (parameters.decimalSeparator.toString ~~ CharIn("0-9").repX).?
    ).!.map(parameters.decimalFormat.parse(_) match {
      case number: java.math.BigDecimal => BigDecimal(number)
    })

  private def const[T: P]: P[BigDecimal] =
    P(IgnoreCase("pi").map(_ => Pi) | (IgnoreCase("e") ~ !CharIn("a-zA-Z")).map(_ => E))

  private def parens[T: P]: P[BigDecimal] = P("(" ~/ addSub ~ ")")

  private def multipleParametersParser[T: P](count: Int): P[Seq[BigDecimal]] =
    P(term.rep(count) ~ !parameters.parameterSeparator.toString) |
      P(term.rep(count, sep = parameters.parameterSeparator.toString)) |
      P("(" ~ addSub.rep(count, sep = parameters.parameterSeparator.toString) ~ ")")
}

object CalculatorUtil {

  private val Pi: BigDecimal = BigDecimal("3.14159265358979323846264338327950288419716939937510")

  private val E: BigDecimal = BigDecimal("2.71828182845904523536028747135266249775724709369995")

  private val mc128 = new MathContext(34)

  private def viaDouble(f: Double => Double)(b: BigDecimal): BigDecimal = BigDecimal(f(b.doubleValue))

  private def bigDecimalCeil(b: BigDecimal): BigDecimal =
    BigDecimal(b.bigDecimal.setScale(0, RoundingMode.CEILING))

  private def bigDecimalFloor(b: BigDecimal): BigDecimal =
    BigDecimal(b.bigDecimal.setScale(0, RoundingMode.FLOOR))

  private def bigDecimalRound(b: BigDecimal): BigDecimal =
    BigDecimal(b.bigDecimal.setScale(0, RoundingMode.HALF_UP))

  private def bigDecimalSqrt(b: BigDecimal): BigDecimal =
    BigDecimal(b.bigDecimal.sqrt(mc128))

  /** Uniform, unbiased random BigInt in the inclusive range [lo, hi], via
    * rejection sampling.
    */
  private def randomBigInt(lo: BigInt, hi: BigInt, rng: scala.util.Random): BigInt = {
    val range = hi - lo + 1
    val bitLength = range.bitLength

    @tailrec
    def loop(): BigInt = {
      val candidate = BigInt(bitLength, rng)
      if (candidate < range) candidate else loop()
    }

    lo + loop()
  }

  def helpMessageFunctionsList: String =
    List(
      "",
      "+ - * / // % ^ **",
      "!, choose",
      "acos, asin, atan, atan2, cos, sin, tan, cosh, sinh, tanh, toDeg, toRad",
      "abs, exp, log, log2, ln, sqrt, hypot",
      "ceil, floor, round, gcd, max, min",
      "random",
      "constants: pi, e"
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
        case "+"  => left + right
        case "-"  => left - right
        case "*"  => left * right
        case "/"  => left / right
        case "//" => BigDecimal(left.bigDecimal.divideToIntegralValue(right.bigDecimal))
        case "%"  => left % right
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

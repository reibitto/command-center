package commandcenter.command.util

import fastparse.Parsed.Success
import fastparse.ScalaWhitespace._
import fastparse._

import scala.annotation.tailrec

/*
 * Operator precedence (from highest to lowest):
 * <ul>
 *   <li>()</li>
 *   <li>functions: acos, asin, atan, atan2, choose (Newton's binomial), cos, exp, ! (factorial),
 *   gcd (greatest common divisor), log, sin, sqrt, tan, toDeg, toRad</li>
 *   <li>&#94; (power)</li>
 *   <li>* / %</li>
 *   <li>+ - *</li>
 * </ul>
 */
object CalculatorUtil {
  def evaluate(input: String): Option[Double] =
    parse(input, expression(_)) match {
      case Success(value, _) => Some(value)
      case _                 => None
    }

  private def expression[_: P]: P[Double] = P(addSub ~ End)

  private def addSub[_: P]: P[Double] =
    P(unaryPlusMinus ~ (CharIn("+\\-").! ~/ unaryPlusMinus).rep).map(evaluateOperatorSequence)

  private def unaryPlusMinus[_: P]: P[Double] =
    P(CharIn("+\\-").!.? ~ mulDivMod).map {
      case (Some("-"), value) => -value
      case (_, value)         => value
    }

  private def mulDivMod[_: P]: P[Double] =
    P(power ~ (CharIn("*/%").! ~/ power).rep).map(evaluateOperatorSequence)

  private def power[_: P]: P[Double] =
    P((function ~ ("^" ~/ function).?).map {
      case (base, Some(exp)) => math.pow(base, exp)
      case (base, _)         => base
    })

  private def function[_: P]: P[Double] =
    P(startsWithTerm | acos | asin | atan2 | atan | cos | exp | gcd | log | sin | sqrt | tan | toDeg | toRad)

  private def startsWithTerm[_: P]: P[Double] =
    P(
      for {
        value1 <- term
        value2 <- P("choose" ~/ term)
                    .filter(d => isWholeNonNegative(value1) && isWholeNonNegative(d) && value1 >= d)
                    .map(binomial(value1, _)) |
                    P("!").filter(_ => isWholeNonNegative(value1)).map(_ => factorial(value1)) |
                    P(Pass).map(_ => value1)
      } yield value2
    )

  private def acos[_: P]: P[Double]  = P("acos" ~/ term).map(math.acos)
  private def asin[_: P]: P[Double]  = P("asin" ~/ term).map(math.asin)
  private def atan[_: P]: P[Double]  = P("atan" ~/ term).map(math.atan)
  private def atan2[_: P]: P[Double] =
    P("atan2" ~/ "(" ~ addSub ~ "," ~ addSub ~ ")").map {
      case (y, x) => math.atan2(y, x)
    }
  private def cos[_: P]: P[Double]   = P("cos" ~/ term).map(math.cos)
  private def exp[_: P]: P[Double]   = P("exp" ~/ term).map(math.exp)
  private def gcd[_: P]: P[Double]   =
    P("gcd" ~/ "(" ~ addSub.filter(_.isWhole) ~ "," ~ addSub.filter(_.isWhole) ~ ")").map {
      case (a, b) => BigInt(a.longValue).gcd(BigInt(b.longValue)).doubleValue
    }
  private def log[_: P]: P[Double]   = P("log" ~/ term).map(math.log)
  private def sin[_: P]: P[Double]   = P("sin" ~/ term).map(math.sin)
  private def sqrt[_: P]: P[Double]  = P("sqrt" ~/ term).map(math.sqrt)
  private def tan[_: P]: P[Double]   = P("tan" ~/ term).map(math.tan)
  private def toDeg[_: P]: P[Double] = P("toDeg" ~/ term).map(math.toDegrees)
  private def toRad[_: P]: P[Double] = P("toRad" ~/ term).map(math.toRadians)

  private def term[_: P]: P[Double] = P(number | const | NoCut(parens))

  private def number[_: P]: P[Double] =
    P(CharIn("0-9").rep(1) ~ ("." ~ CharIn("0-9").rep).?).!.map(_.toDouble)

  private def const[_: P]: P[Double] = P(IgnoreCase("pi").map(_ => math.Pi))

  private def parens[_: P]: P[Double] = P("(" ~/ addSub ~ ")")

  private def evaluateOperatorSequence(tree: (Double, Seq[(String, Double)])): Double = {
    val (base, ops) = tree
    ops.foldLeft(base) {
      case (left, (op, right)) =>
        op match {
          case "+" => left + right
          case "-" => left - right
          case "*" => left * right
          case "/" => left / right
          case "%" => left % right
        }
    }
  }

  private def isWholeNonNegative(b: Double) = b.isWhole && b >= 0

  private def binomial(n: Double, r: Double): Double = {
    @tailrec
    def binomialRec(n: BigInt, r: BigInt, i: BigInt, acc: BigInt): BigInt =
      if (i >= r) acc
      else binomialRec(n, r, i + 1, acc * (n - i) / (i + 1))

    val r2 = if (2 * r <= n) r else n - r
    binomialRec(BigInt(n.longValue), BigInt(r2.longValue), 0, 1).doubleValue
  }

  private def factorial(b: Double): Double = {
    @tailrec
    def factorialRec(b: BigInt, acc: BigInt): BigInt = if (b <= 1) acc else factorialRec(b - 1, b * acc)

    factorialRec(BigInt(b.longValue), 1).doubleValue
  }
}

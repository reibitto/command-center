package commandcenter.command

import cats.syntax.apply._
import com.monovore.decline
import com.monovore.decline.Opts
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.util.ProcessUtil
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ UIO, ZIO }

final case class RadixCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.RadixCommand

  val commandNames: List[String] = List("radix", "base")

  val title: String = "Convert base"

  val fromRadixOpt = Opts.option[Int]("from", "Radix to convert from", "f").orNone
  val toRadixOpt   = Opts.option[Int]("to", "Radix to convert to", "t").orNone
  val numberArg    = Opts.argument[String]("number")

  val radixCommand = decline.Command("radix", title)((fromRadixOpt, toRadixOpt, numberArg).tupled)

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): ZIO[Env, CommandError, List[PreviewResult[Unit]]] = {
    val parsed = radixCommand.parse(args)

    for {
      message <- ZIO
                  .fromEither(parsed)
                  .foldM(
                    help => UIO(HelpMessage.formatted(help)), {
                      case (fromRadixOpt, toRadixOpt, number) =>
                        val fromRadix = fromRadixOpt.getOrElse(10)
                        val toRadix   = toRadixOpt.getOrElse(10)
                        val n         = java.lang.Long.valueOf(number, fromRadix)
                        val formatted = java.lang.Long.toString(n, toRadix)

                        UIO(fansi.Str(s"$formatted"))
                    }
                  )
    } yield {
      List(
        Preview.unit
          .score(Scores.high(context))
          .onRun(ProcessUtil.copyToClipboard(message.plainText))
          .view(DefaultView(title, message))
      )
    }
  }

}

object RadixCommand extends CommandPlugin[RadixCommand] {
  implicit val decoder: Decoder[RadixCommand] = Decoder.const(RadixCommand())
}

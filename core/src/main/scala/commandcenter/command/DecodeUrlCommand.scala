package commandcenter.command

import java.net.URLDecoder

import cats.syntax.apply._
import com.monovore.decline
import commandcenter.CommandContext
import CommonOpts._
import commandcenter.util.ProcessUtil
import io.circe.Decoder
import zio.{ IO, Task }

final case class DecodeUrlCommand() extends Command[String] {
  val command                    = "decodeurl"
  val commandType: CommandType   = CommandType.DecodeUrlCommand
  val commandNames: List[String] = List(command)
  val title: String              = "Decode (URL)"

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): IO[CommandError, List[PreviewResult[String]]] = {
    val all           = (stringArg, encodingOpt).tupled
    val parsedCommand = decline.Command(command, s"URL decodes the given string")(all).parse(args)

    for {
      (valueToDecode, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      decoded                  <- Task(URLDecoder.decode(valueToDecode, charset)).mapError(CommandError.UnexpectedException)
    } yield List(Preview(decoded).onRun(ProcessUtil.copyToClipboard(decoded)).score(Scores.high(context)))
  }
}

object DecodeUrlCommand extends CommandPlugin[DecodeUrlCommand] {
  implicit val decoder: Decoder[DecodeUrlCommand] = Decoder.const(DecodeUrlCommand())
}

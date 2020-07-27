package commandcenter.command

import java.net.URLEncoder

import cats.syntax.apply._
import com.monovore.decline
import commandcenter.CommandContext
import CommonOpts._
import commandcenter.util.ProcessUtil
import io.circe.Decoder
import zio.IO

final case class EncodeUrlCommand() extends Command[String] {
  val command                  = "encodeurl"
  val commandType: CommandType = CommandType.EncodeUrlCommand

  val commandNames: List[String] = List(command)

  val title: String = "Encode (URL)"

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): IO[CommandError, List[PreviewResult[String]]] = {
    val all           = (stringArg, encodingOpt).tupled
    val parsedCommand = decline.Command(command, s"URL encodes the given string")(all).parse(args)

    for {
      (valueToEncode, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      encoded                  = URLEncoder.encode(valueToEncode, charset)
    } yield List(
      Preview(encoded).onRun(ProcessUtil.copyToClipboard(encoded)).score(Scores.high(context))
    )
  }
}

object EncodeUrlCommand extends CommandPlugin[EncodeUrlCommand] {
  implicit val decoder: Decoder[EncodeUrlCommand] = Decoder.const(EncodeUrlCommand())
}

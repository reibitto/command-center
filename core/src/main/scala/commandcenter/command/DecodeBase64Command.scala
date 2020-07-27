package commandcenter.command

import java.util.Base64

import cats.syntax.apply._
import com.monovore.decline
import commandcenter.CommandContext
import CommonOpts._
import commandcenter.util.ProcessUtil
import io.circe.Decoder
import zio.IO

final case class DecodeBase64Command() extends Command[String] {
  val command                    = "decodebase64"
  val commandType: CommandType   = CommandType.DecodeBase64Command
  val commandNames: List[String] = List(command)
  val title: String              = "Decode (Base64)"

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): IO[CommandError, List[PreviewResult[String]]] = {
    val all           = (stringArg, encodingOpt).tupled
    val parsedCommand = decline.Command(command, s"Base64 decodes the given string")(all).parse(args)

    for {
      (valueToDecode, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      decoded                  = new String(Base64.getDecoder.decode(valueToDecode.getBytes(charset)), charset)
    } yield {
      List(Preview(decoded).onRun(ProcessUtil.copyToClipboard(decoded)).score(Scores.high(context)))
    }
  }
}

object DecodeBase64Command extends CommandPlugin[DecodeBase64Command] {
  implicit val decoder: Decoder[DecodeBase64Command] = Decoder.const(DecodeBase64Command())
}

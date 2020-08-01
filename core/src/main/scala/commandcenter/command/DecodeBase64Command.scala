package commandcenter.command

import java.util.Base64

import cats.syntax.apply._
import com.monovore.decline
import commandcenter.CCRuntime.Env
import commandcenter.command.CommonOpts._
import io.circe.Decoder
import zio.ZIO

final case class DecodeBase64Command() extends Command[String] {
  val command                    = "decodebase64"
  val commandType: CommandType   = CommandType.DecodeBase64Command
  val commandNames: List[String] = List(command)
  val title: String              = "Decode (Base64)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input                    <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all                      = (stringArg, encodingOpt).tupled
      parsedCommand            = decline.Command(command, s"Base64 decodes the given string")(all).parse(input.args)
      (valueToDecode, charset) <- ZIO.fromEither(parsedCommand).mapError(CommandError.CliError)
      decoded                  = new String(Base64.getDecoder.decode(valueToDecode.getBytes(charset)), charset)
    } yield {
      List(Preview(decoded).onRun(input.context.ccProcess.setClipboard(decoded)).score(Scores.high(input.context)))
    }
}

object DecodeBase64Command extends CommandPlugin[DecodeBase64Command] {
  implicit val decoder: Decoder[DecodeBase64Command] = Decoder.const(DecodeBase64Command())
}

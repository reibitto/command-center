package commandcenter.command

import java.net.URLEncoder

import cats.syntax.apply._
import com.monovore.decline
import commandcenter.CCRuntime.Env
import commandcenter.command.CommonOpts._
import io.circe.Decoder
import zio.{ IO, ZIO }

final case class EncodeUrlCommand() extends Command[String] {
  val command                  = "encodeurl"
  val commandType: CommandType = CommandType.EncodeUrlCommand

  val commandNames: List[String] = List(command)

  val title: String = "Encode (URL)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input                    <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all                      = (stringArg, encodingOpt).tupled
      parsedCommand            = decline.Command(command, s"URL encodes the given string")(all).parse(input.args)
      (valueToEncode, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      encoded                  = URLEncoder.encode(valueToEncode, charset)
    } yield List(
      Preview(encoded).onRun(searchInput.context.ccProcess.setClipboard(encoded)).score(Scores.high(input.context))
    )
}

object EncodeUrlCommand extends CommandPlugin[EncodeUrlCommand] {
  implicit val decoder: Decoder[EncodeUrlCommand] = Decoder.const(EncodeUrlCommand())
}

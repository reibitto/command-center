package commandcenter.command

import java.util.Base64

import cats.syntax.apply._
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommonOpts._
import commandcenter.tools
import zio.{ IO, TaskManaged, ZIO, ZManaged }

final case class EncodeBase64Command() extends Command[String] {
  val command                    = "encodebase64"
  val commandType: CommandType   = CommandType.EncodeBase64Command
  val commandNames: List[String] = List(command)
  val title: String              = "Encode (Base64)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input                    <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all                       = (stringArg, encodingOpt).tupled
      parsedCommand             = decline.Command(command, s"Base64 encodes the given string")(all).parse(input.args)
      (valueToEncode, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      encoded                   = Base64.getEncoder.encodeToString(valueToEncode.getBytes(charset))
    } yield List(
      Preview(encoded).onRun(tools.setClipboard(encoded)).score(Scores.high(input.context))
    )
}

object EncodeBase64Command extends CommandPlugin[EncodeBase64Command] {
  def make(config: Config): TaskManaged[EncodeBase64Command] = ZManaged.succeed(EncodeBase64Command())
}

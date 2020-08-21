package commandcenter.command

import java.net.URLEncoder

import cats.syntax.apply._
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommonOpts._
import commandcenter.tools
import zio.{ IO, TaskManaged, ZIO, ZManaged }

final case class EncodeUrlCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.EncodeUrlCommand

  val title: String = "Encode (URL)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input                    <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all                       = (stringArg, encodingOpt).tupled
      parsedCommand             = decline.Command("", s"URL encodes the given string")(all).parse(input.args)
      (valueToEncode, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      encoded                   = URLEncoder.encode(valueToEncode, charset)
    } yield List(
      Preview(encoded).onRun(tools.setClipboard(encoded)).score(Scores.high(input.context))
    )
}

object EncodeUrlCommand extends CommandPlugin[EncodeUrlCommand] {
  def make(config: Config): TaskManaged[EncodeUrlCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield EncodeUrlCommand(commandNames.getOrElse(List("encodeurl")))
    )
}

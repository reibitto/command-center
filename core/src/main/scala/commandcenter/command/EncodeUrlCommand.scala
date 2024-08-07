package commandcenter.command

import cats.syntax.apply.*
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.command.CommonOpts.*
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.*

import java.net.URLEncoder

final case class EncodeUrlCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.EncodeUrlCommand

  val title: String = "Encode (URL)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all = (stringArg, encodingOpt).tupled
      parsedCommand = decline.Command("", s"URL encodes the given string")(all).parse(input.args)
      (valueToEncode, charset) <- ZIO.fromEither(parsedCommand).mapError(CommandError.CliError.apply)
      encoded = URLEncoder.encode(valueToEncode, charset)
    } yield PreviewResults.one(
      Preview(encoded).onRun(Tools.setClipboard(encoded)).score(Scores.veryHigh(input.context))
    )
}

object EncodeUrlCommand extends CommandPlugin[EncodeUrlCommand] {

  def make(config: Config): IO[CommandPluginError, EncodeUrlCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield EncodeUrlCommand(commandNames.getOrElse(List("encodeurl")))
}

package commandcenter.command

import cats.syntax.apply.*
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.command.CommonOpts.*
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.*

import java.net.URLDecoder

final case class DecodeUrlCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.DecodeUrlCommand
  val title: String = "Decode (URL)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all = (stringArg, encodingOpt).tupled
      parsedCommand = decline.Command("", s"URL decodes the given string")(all).parse(input.args)
      (valueToDecode, charset) <- ZIO.fromEither(parsedCommand).mapError(CommandError.CliError)
      decoded                  <- ZIO.attempt(URLDecoder.decode(valueToDecode, charset)).mapError(CommandError.UnexpectedException)
    } yield PreviewResults.one(
      Preview(decoded).onRun(Tools.setClipboard(decoded)).score(Scores.high(input.context))
    )
}

object DecodeUrlCommand extends CommandPlugin[DecodeUrlCommand] {

  def make(config: Config): IO[CommandPluginError, DecodeUrlCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield DecodeUrlCommand(commandNames.getOrElse(List("decodeurl")))
}

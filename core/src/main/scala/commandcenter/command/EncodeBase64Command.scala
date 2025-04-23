package commandcenter.command

import cats.syntax.apply.*
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.command.CommonOpts.*
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.*

import java.util.Base64

final case class EncodeBase64Command(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.EncodeBase64Command
  val title: String = "Encode (Base64)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all = (stringArg, encodingOpt).tupled
      parsedCommand = decline.Command("", s"Base64 encodes the given string")(all).parse(input.args)
      (valueToEncode, charset) <- ZIO.fromEither(parsedCommand).mapError(CommandError.CliError.apply)
      encoded = Base64.getEncoder.encodeToString(valueToEncode.getBytes(charset))
    } yield PreviewResults.one(
      Preview(encoded).onRun(Tools.setClipboard(encoded)).score(Scores.veryHigh(input.context))
    )
}

object EncodeBase64Command extends CommandPlugin[EncodeBase64Command] {

  def make(config: Config): IO[CommandPluginError, EncodeBase64Command] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield EncodeBase64Command(commandNames.getOrElse(List("encodebase64")))
}

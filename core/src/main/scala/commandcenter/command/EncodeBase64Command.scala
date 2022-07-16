package commandcenter.command

import cats.syntax.apply.*
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.command.CommonOpts.*
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.{IO, Managed, ZIO}

import java.util.Base64

final case class EncodeBase64Command(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.EncodeBase64Command
  val title: String = "Encode (Base64)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all = (stringArg, encodingOpt).tupled
      parsedCommand = decline.Command("", s"Base64 encodes the given string")(all).parse(input.args)
      (valueToEncode, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      encoded = Base64.getEncoder.encodeToString(valueToEncode.getBytes(charset))
    } yield PreviewResults.one(
      Preview(encoded).onRun(Tools.setClipboard(encoded)).score(Scores.high(input.context))
    )
}

object EncodeBase64Command extends CommandPlugin[EncodeBase64Command] {

  def make(config: Config): Managed[CommandPluginError, EncodeBase64Command] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
    } yield EncodeBase64Command(commandNames.getOrElse(List("decodeurl")))
}

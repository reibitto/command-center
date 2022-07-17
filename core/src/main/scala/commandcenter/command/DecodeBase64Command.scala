package commandcenter.command

import cats.syntax.apply.*
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.command.CommonOpts.*
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import zio.managed.*
import zio.ZIO

import java.util.Base64

final case class DecodeBase64Command(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.DecodeBase64Command
  val title: String = "Decode (Base64)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all = (stringArg, encodingOpt).tupled
      parsedCommand = decline.Command("", s"Base64 decodes the given string")(all).parse(input.args)
      (valueToDecode, charset) <- ZIO.fromEither(parsedCommand).mapError(CommandError.CliError)
      decoded = new String(Base64.getDecoder.decode(valueToDecode.getBytes(charset)), charset)
    } yield PreviewResults.one(
      Preview(decoded).onRun(Tools.setClipboard(decoded)).score(Scores.high(input.context))
    )
}

object DecodeBase64Command extends CommandPlugin[DecodeBase64Command] {

  def make(config: Config): Managed[CommandPluginError, DecodeBase64Command] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
    } yield DecodeBase64Command(commandNames.getOrElse(List("decodebase64")))
}

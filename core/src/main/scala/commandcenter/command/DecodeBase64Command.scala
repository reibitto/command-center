package commandcenter.command

import java.util.Base64

import cats.syntax.apply._
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommonOpts._
import commandcenter.tools
import zio.{ TaskManaged, ZIO, ZManaged }

final case class DecodeBase64Command(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.DecodeBase64Command
  val title: String            = "Decode (Base64)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input                    <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all                       = (stringArg, encodingOpt).tupled
      parsedCommand             = decline.Command("", s"Base64 decodes the given string")(all).parse(input.args)
      (valueToDecode, charset) <- ZIO.fromEither(parsedCommand).mapError(CommandError.CliError)
      decoded                   = new String(Base64.getDecoder.decode(valueToDecode.getBytes(charset)), charset)
    } yield PreviewResults.one(
      Preview(decoded).onRun(tools.setClipboard(decoded)).score(Scores.high(input.context))
    )
}

object DecodeBase64Command extends CommandPlugin[DecodeBase64Command] {
  def make(config: Config): TaskManaged[DecodeBase64Command] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield DecodeBase64Command(commandNames.getOrElse(List("decodebase64")))
    )
}

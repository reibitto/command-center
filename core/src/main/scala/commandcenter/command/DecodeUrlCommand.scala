package commandcenter.command

import cats.syntax.apply._
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommonOpts._
import commandcenter.tools.Tools
import zio._

import java.net.URLDecoder

final case class DecodeUrlCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.DecodeUrlCommand
  val title: String            = "Decode (URL)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input                    <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all                       = (stringArg, encodingOpt).tupled
      parsedCommand             = decline.Command("", s"URL decodes the given string")(all).parse(input.args)
      (valueToDecode, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      decoded                  <- Task(URLDecoder.decode(valueToDecode, charset)).mapError(CommandError.UnexpectedException)
    } yield PreviewResults.one(
      Preview(decoded).onRun(Tools.setClipboard(decoded)).score(Scores.high(input.context))
    )
}

object DecodeUrlCommand extends CommandPlugin[DecodeUrlCommand] {
  def make(config: Config): TaskManaged[DecodeUrlCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield DecodeUrlCommand(commandNames.getOrElse(List("decodeurl")))
    )
}

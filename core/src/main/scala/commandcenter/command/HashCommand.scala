package commandcenter.command

import cats.syntax.apply._
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommonOpts._
import commandcenter.command.util.HashUtil
import commandcenter.tools
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ IO, TaskManaged, ZIO, ZManaged }

final case class HashCommand(algorithm: String) extends Command[String] {
  val commandType: CommandType   = CommandType.HashCommand
  val commandNames: List[String] = List(algorithm, algorithm.replace("-", "")).distinct
  val title: String              = algorithm

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input                  <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all                     = (stringArg, encodingOpt).tupled
      parsedCommand           = decline.Command(algorithm, s"Hashes the argument with $algorithm")(all).parse(input.args)
      (valueToHash, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      hashResult             <- IO
                                  .fromEither(HashUtil.hash(algorithm)(valueToHash, charset))
                                  .mapError(CommandError.UnexpectedException)
    } yield PreviewResults.one(
      Preview(hashResult)
        .score(Scores.high(input.context))
        .onRun(tools.setClipboard(hashResult))
        .render(result => DefaultView(algorithm, result))
    )
}

object HashCommand extends CommandPlugin[HashCommand] {
  implicit val decoder: Decoder[HashCommand] = Decoder.forProduct1("algorithm")(HashCommand.apply)

  def make(config: Config): TaskManaged[HashCommand] = ZManaged.fromEither(config.as[HashCommand])
}

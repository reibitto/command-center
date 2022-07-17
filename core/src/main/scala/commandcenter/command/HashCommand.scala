package commandcenter.command

import cats.syntax.apply.*
import com.monovore.decline
import com.typesafe.config.Config
import commandcenter.command.util.HashUtil
import commandcenter.command.CommonOpts.*
import commandcenter.tools.Tools
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import io.circe.Decoder
import zio.managed.*
import zio.ZIO

final case class HashCommand(algorithm: String) extends Command[String] {
  val commandType: CommandType = CommandType.HashCommand
  val commandNames: List[String] = List(algorithm, algorithm.replace("-", "")).distinct
  val title: String = algorithm

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      all = (stringArg, encodingOpt).tupled
      parsedCommand = decline.Command(algorithm, s"Hashes the argument with $algorithm")(all).parse(input.args)
      (valueToHash, charset) <- ZIO.fromEither(parsedCommand).mapError(CommandError.CliError)
      hashResult <- ZIO
                      .fromEither(HashUtil.hash(algorithm)(valueToHash, charset))
                      .mapError(CommandError.UnexpectedException)
    } yield PreviewResults.one(
      Preview(hashResult)
        .score(Scores.high(input.context))
        .onRun(Tools.setClipboard(hashResult))
        .rendered(Renderer.renderDefault(algorithm, hashResult))
    )
}

object HashCommand extends CommandPlugin[HashCommand] {
  implicit val decoder: Decoder[HashCommand] = Decoder.forProduct1("algorithm")(HashCommand.apply)

  def make(config: Config): Managed[CommandPluginError, HashCommand] =
    ZManaged.fromEither(config.as[HashCommand]).mapError(CommandPluginError.UnexpectedException)
}

package commandcenter.command

import cats.syntax.apply._
import com.monovore.decline
import commandcenter.CommandContext
import CommonOpts._
import commandcenter.command.util.HashUtil
import commandcenter.util.ProcessUtil
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.IO

final case class HashCommand(algorithm: String) extends Command[String] {
  val commandType: CommandType   = CommandType.HashCommand
  val commandNames: List[String] = List(algorithm, algorithm.replace("-", "")).distinct
  val title: String              = algorithm

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): IO[CommandError, List[PreviewResult[String]]] = {
    val all           = (stringArg, encodingOpt).tupled
    val parsedCommand = decline.Command(algorithm, s"Hashes the argument with $algorithm")(all).parse(args)

    for {
      (valueToHash, charset) <- IO.fromEither(parsedCommand).mapError(CommandError.CliError)
      hashResult <- IO
                     .fromEither(HashUtil.hash(algorithm)(valueToHash, charset))
                     .mapError(CommandError.UnexpectedException)
    } yield {
      List(
        Preview(hashResult)
          .score(Scores.high(context))
          .onRun(ProcessUtil.copyToClipboard(hashResult))
          .render(result => DefaultView(algorithm, result))
      )
    }
  }
}

object HashCommand extends CommandPlugin[HashCommand] {
  implicit val decoder: Decoder[HashCommand] = Decoder.forProduct1("algorithm")(HashCommand.apply)
}

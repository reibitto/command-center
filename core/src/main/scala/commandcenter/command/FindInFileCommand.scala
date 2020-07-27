package commandcenter.command

import java.io.File

import commandcenter.CommandContext
import commandcenter.command.CommandError._
import commandcenter.util.OS
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO
import zio.blocking.Blocking
import zio.process.{ Command => PCommand }

final case class FindInFileCommand() extends Command[File] {
  val commandType: CommandType = CommandType.FindInFileCommand

  val commandNames: List[String] = List("in")

  val title: String = "Find in files"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  override def prefixPreview(
    prefix: String,
    rest: String,
    context: CommandContext
  ): ZIO[Blocking, CommandError, List[PreviewResult[File]]] =
    // TODO: mdfind/Spotlight can be really slow, especially when there are a lot of matches. Streaming the top N results
    // only seems to help a little bit (there appears to be no "top N" command line option for mdfind). This needs some
    // investigation. Or look into alternative approaches.
    if (rest.length < 3) ZIO.fail(NotApplicable) // TODO: Show feedback to user in this case?
    else
      (for {
        lines <- PCommand("mdfind", rest).linesStream.take(8).runCollect.map(_.toList)
        files = lines.map(new File(_))
        // TODO: The default sorting is awful with mdfind. Should we intervene somehow? That would conflict a bit with
        // `take(N)` though.
        results = files.map { file =>
          Preview(file)
            .view(DefaultView(file.getAbsolutePath, "Open file"))
            .onRun(PCommand("open", file.getAbsolutePath).exitCode.unit)
            .score(Scores.high(context))
        }
      } yield results).mapError(UnexpectedException)
}

object FindInFileCommand extends CommandPlugin[FindInFileCommand] {
  implicit val decoder: Decoder[FindInFileCommand] = Decoder.const(FindInFileCommand())
}

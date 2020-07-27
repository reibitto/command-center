package commandcenter.command

import java.io.File

import commandcenter.CommandContext
import commandcenter.command.CommandError._
import commandcenter.command.util.PathUtil
import commandcenter.util.OS
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO
import zio.blocking.Blocking
import zio.process.{ Command => PCommand }

final case class FindFileCommand() extends Command[File] {
  val commandType: CommandType = CommandType.FindFileCommand

  val commandNames: List[String] = List("find")

  val title: String = "Find files"

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
        lines <- PCommand("mdfind", "-name", rest).linesStream.take(8).runCollect.map(_.toList)
        files = lines.map(new File(_))
        // TODO: The default sorting is awful with mdfind. Should we intervene somehow? That would conflict a bit with
        // `take(N)` though.
        results = files.map { file =>
          val shortenedPath = PathUtil.shorten(file.getAbsolutePath)

          Preview(file)
            .view(DefaultView(shortenedPath, "Open file"))
            .onRun(PCommand("open", file.getAbsolutePath).exitCode.unit)
            .score(Scores.high(context))
        }
      } yield results).mapError(UnexpectedException)
}

object FindFileCommand extends CommandPlugin[FindFileCommand] {
  implicit val decoder: Decoder[FindFileCommand] = Decoder.const(FindFileCommand())
}

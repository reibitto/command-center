package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommandError._
import commandcenter.command.util.PathUtil
import commandcenter.util.OS
import commandcenter.view.Renderer
import zio.process.{ Command => PCommand }
import zio.{ Managed, ZIO }

import java.io.File

final case class FindFileCommand(commandNames: List[String]) extends Command[File] {
  val commandType: CommandType = CommandType.FindFileCommand
  val title: String            = "Find files"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[File]] =
    for {
      input  <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      result <- // TODO: mdfind/Spotlight can be really slow, especially when there are a lot of matches. Streaming the top N results
        // only seems to help a little bit (there appears to be no "top N" command line option for mdfind). This needs some
        // investigation. Or look into alternative approaches.
        if (input.rest.length < 3) ZIO.fail(NotApplicable) // TODO: Show feedback to user in this case?
        else
          (for {
            lines  <- PCommand("mdfind", "-name", input.rest).linesStream.take(8).runCollect
            files   = lines.map(new File(_))
            // TODO: The default sorting is awful with mdfind. Should we intervene somehow? That would conflict a bit with
            // `take(N)` though.
            results = files.map { file =>
                        val shortenedPath = PathUtil.shorten(file.getAbsolutePath)

                        Preview(file)
                          .rendered(Renderer.renderDefault(shortenedPath, "Open file"))
                          .onRun(PCommand("open", file.getAbsolutePath).exitCode.unit)
                          .score(Scores.high(input.context))
                      }
          } yield PreviewResults.fromIterable(results)).mapError(UnexpectedException)
    } yield result
}

object FindFileCommand extends CommandPlugin[FindFileCommand] {
  def make(config: Config): Managed[CommandPluginError, FindFileCommand] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
    } yield FindFileCommand(commandNames.getOrElse(List("find")))
}

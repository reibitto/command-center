package commandcenter.command

import java.io.File

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommandError._
import commandcenter.util.OS
import commandcenter.view.DefaultView
import zio.process.{ Command => PCommand }
import zio.{ TaskManaged, ZIO, ZManaged }

final case class FindInFileCommand(commandNames: List[String]) extends Command[File] {
  val commandType: CommandType = CommandType.FindInFileCommand
  val title: String            = "Find in files"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[File]]] =
    for {
      input  <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      // TODO: mdfind/Spotlight can be really slow, especially when there are a lot of matches. Streaming the top N results
      // only seems to help a little bit (there appears to be no "top N" command line option for mdfind). This needs some
      // investigation. Or look into alternative approaches.
      result <- if (input.rest.length < 3) ZIO.fail(NotApplicable) // TODO: Show feedback to user in this case?
                else
                  (for {
                    lines  <- PCommand("mdfind", input.rest).linesStream.take(8).runCollect.map(_.toList)
                    files   = lines.map(new File(_))
                    // TODO: The default sorting is awful with mdfind. Should we intervene somehow? That would conflict a bit with
                    // `take(N)` though.
                    results = files.map { file =>
                                Preview(file)
                                  .view(DefaultView(file.getAbsolutePath, "Open file"))
                                  .onRun(PCommand("open", file.getAbsolutePath).exitCode.unit)
                                  .score(Scores.high(input.context))
                              }
                  } yield results).mapError(UnexpectedException)
    } yield result
}

object FindInFileCommand extends CommandPlugin[FindInFileCommand] {
  def make(config: Config): TaskManaged[FindInFileCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield FindInFileCommand(commandNames.getOrElse(List("in")))
    )
}

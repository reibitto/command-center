package commandcenter.command

import com.typesafe.config.Config
import commandcenter.command.CommandError.*
import commandcenter.util.ProcessUtil
import commandcenter.CCRuntime.Env
import zio.*
import zio.stream.ZStream

import java.io.File
import java.nio.file.Files
import scala.util.matching.Regex

final case class FileNavigationCommand(homeDirectory: Option[String]) extends Command[File] {
  val commandType: CommandType = CommandType.FileNavigationCommand

  val commandNames: List[String] = List.empty

  val title: String = "File navigation"

  // For Windows-style paths like `C:/folder`
  val drivePathRegex: Regex = "[A-Za-z]:".r

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[File]] = {
    val input = searchInput.input
    if (!input.startsWith("/") && !input.startsWith("~/") && drivePathRegex.findPrefixOf(input).isEmpty)
      ZIO.fail(NotApplicable)
    else {
      val file = homeDirectory match {
        case Some(home) if input == "~/"          => new File(home)
        case Some(home) if input.startsWith("~/") => new File(home, input.tail)
        case _                                    => new File(input)
      }

      val exists = file.exists()
      val score = if (exists) 101 else 100
      val titleColor = if (exists) fansi.Color.Blue else fansi.Color.Red

      val sameLevel = Option(file.getParentFile)
        .map(f => ZStream.fromJavaStream(Files.list(f.toPath)).catchAll(_ => ZStream.empty))
        .getOrElse(ZStream.empty)
        .map(_.toFile)
        .filter { f =>
          val listedPath = f.getAbsolutePath.toLowerCase
          val inputFile = file.getAbsolutePath.toLowerCase
          listedPath.startsWith(inputFile) && listedPath.length != inputFile.length
        }

      val children = Option
        .when(file.isDirectory)(file)
        .filter(_.isDirectory)
        .map(f => ZStream.fromJavaStream(Files.list(f.toPath)).catchAll(_ => ZStream.empty))
        .getOrElse(ZStream.empty)
        .map(_.toFile)
        .filter { f =>
          val listedPath = f.getAbsolutePath.toLowerCase
          val inputFile = file.getAbsolutePath.toLowerCase
          listedPath.startsWith(inputFile) && listedPath.length != inputFile.length
        }

      ZIO.succeed {
        PreviewResults.paginated(
          ZStream.succeed(
            Preview(file)
              .score(score)
              .renderedAnsi(titleColor(file.getAbsolutePath) ++ fansi.Str(" Open file location"))
              .onRun(ProcessUtil.browseToFile(file))
          ) ++ (sameLevel ++ children).map { f =>
            Preview(f)
              .score(score)
              .renderedAnsi(fansi.Color.Blue(f.getAbsolutePath) ++ fansi.Str(" Open file location"))
              .onRun(ProcessUtil.browseToFile(f))
          },
          initialPageSize = 10,
          morePageSize = 20
        )
      }
    }
  }
}

object FileNavigationCommand extends CommandPlugin[FileNavigationCommand] {

  def make(config: Config): IO[CommandPluginError, FileNavigationCommand] =
    for {
      homeDirectory <- System.property("user.home").catchAll { t =>
                         ZIO.logWarningCause("Could not obtain location of home directory", Cause.die(t)).as(None)
                       }
    } yield FileNavigationCommand(homeDirectory)
}

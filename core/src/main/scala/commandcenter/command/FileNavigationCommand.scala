package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommandError._
import commandcenter.util.ProcessUtil
import zio._

import java.io.File
import scala.util.Try
import scala.util.matching.Regex

final case class FileNavigationCommand() extends Command[File] {
  val commandType: CommandType = CommandType.FileNavigationCommand

  val commandNames: List[String] = List.empty

  val title: String = "File navigation"

  val homeDirectory: Option[String] = Try(System.getProperty("user.home")).toOption

  // For Windows-style paths like `C:/folder`
  val drivePathRegex: Regex = "[A-Za-z]:".r

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[File]]] = {
    val input = searchInput.input
    if (!input.startsWith("/") && !input.startsWith("~/") && drivePathRegex.findPrefixOf(input).isEmpty)
      IO.fail(NotApplicable)
    else {
      val file = homeDirectory match {
        case Some(home) if input == "~/"          => new File(home)
        case Some(home) if input.startsWith("~/") => new File(home, input.tail)
        case _                                    => new File(input)
      }

      val exists     = file.exists()
      val score      = if (exists) 101 else 100
      val titleColor = if (exists) fansi.Color.Blue else fansi.Color.Red

      // TODO: This can be made more efficient. Also improve the view, such as highlighting the matched part in a different color
      val sameLevel = Option(file.getParentFile)
        .map(_.listFiles.toList)
        .getOrElse(List.empty)
        .filter(f => f.getAbsolutePath.startsWith(file.getAbsolutePath) && f != file)
        .take(5)
        .map { f =>
          Preview(f)
            .score(score)
            .view(fansi.Color.Blue(f.getAbsolutePath) ++ fansi.Str(" Open file location"))
            .onRun(ProcessUtil.browseFile(f))
        }

      val children = Option(file)
        .filter(_.isDirectory)
        .map(_.listFiles.toList)
        .getOrElse(List.empty)
        .filter(f => f.getAbsolutePath.startsWith(file.getAbsolutePath) && f != file)
        .take(5)
        .map { f =>
          Preview(f)
            .score(score)
            .view(fansi.Color.Blue(f.getAbsolutePath) ++ fansi.Str(" Open file location"))
            .onRun(ProcessUtil.browseFile(f))
        }

      UIO {
        List(
          Preview(file)
            .score(score)
            .view(titleColor(file.getAbsolutePath) ++ fansi.Str(" Open file location"))
            .onRun(ProcessUtil.browseFile(file))
        ) ++ sameLevel ++ children
      }
    }
  }
}

object FileNavigationCommand extends CommandPlugin[FileNavigationCommand] {
  def make(config: Config): UManaged[FileNavigationCommand] = ZManaged.succeed(FileNavigationCommand())
}

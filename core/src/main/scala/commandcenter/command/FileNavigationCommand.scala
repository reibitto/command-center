package commandcenter.command

import java.io.File

import commandcenter.CommandContext
import commandcenter.command.CommandError._
import io.circe.Decoder
import zio.blocking.Blocking
import zio.{ IO, UIO, ZIO }

import scala.util.Try

final case class FileNavigationCommand() extends Command[File] {
  val commandType: CommandType = CommandType.FileNavigationCommand

  val commandNames: List[String] = List.empty

  val title: String = "File navigation"

  val homeDirectory: Option[String] = None //Try(System.getProperty("user.home")).toOption

  override def inputPreview(
    input: String,
    context: CommandContext
  ): ZIO[Blocking, CommandError, List[PreviewResult[File]]] =
    if (!input.startsWith("/") && !input.startsWith("~/")) {
      IO.fail(NotApplicable)
    } else {
      val file = homeDirectory match {
        case Some(home) if input == "~/" =>
          new File(home)

        case Some(home) if input.startsWith("~/") =>
          new File(home, input.tail)

        case _ => new File(input)
      }

      val exists     = file.exists()
      val score      = if (exists) 100 else 10
      val titleColor = if (exists) fansi.Color.Blue else fansi.Color.Red

      // TODO: This can be made more efficient. Also improve the view, such as highlighting the matched part in a different color
      val sameLevel = Option(file.getParentFile)
        .map(_.listFiles.toList)
        .getOrElse(List.empty)
        .filter(f => f.getAbsolutePath.startsWith(file.getAbsolutePath) && f != file)
        .take(5)
        .map(f => Preview(f).score(score).view(fansi.Color.Blue(f.getAbsolutePath) ++ fansi.Str(" Open file")))

      val children = Option(file)
        .filter(_.isDirectory)
        .map(_.listFiles.toList)
        .getOrElse(List.empty)
        .filter(f => f.getAbsolutePath.startsWith(file.getAbsolutePath) && f != file)
        .take(5)
        .map(f => Preview(f).score(score).view(fansi.Color.Blue(f.getAbsolutePath) ++ fansi.Str(" Open file")))

      UIO {
        List(
          Preview(file).score(score).view(titleColor(file.getAbsolutePath) ++ fansi.Str(" Open file"))
        ) ++ sameLevel ++ children
      }
    }
}

object FileNavigationCommand extends CommandPlugin[FileNavigationCommand] {
  implicit val decoder: Decoder[FileNavigationCommand] = Decoder.const(FileNavigationCommand())
}

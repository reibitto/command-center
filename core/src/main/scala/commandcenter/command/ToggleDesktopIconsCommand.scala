package commandcenter.command

import commandcenter.CommandContext
import commandcenter.util.OS
import io.circe.Decoder
import zio.process.{ Command => PCommand }
import zio.{ IO, UIO }

final case class ToggleDesktopIconsCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ToggleDesktopIconsCommand

  val commandNames: List[String] = List("icons")

  val title: String = "Toggle Desktop Icons"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  override def keywordPreview(
    keyword: String,
    context: CommandContext
  ): IO[CommandError, List[PreviewResult[Unit]]] = {
    val run = for {
      showingIcons <- PCommand("defaults", "read", "com.apple.finder", "CreateDesktop").string.map(_.trim == "1")
      _            <- PCommand("defaults", "write", "com.apple.finder", "CreateDesktop", "-bool", (!showingIcons).toString).exitCode
      _            <- PCommand("killall", "Finder").exitCode
    } yield ()

    UIO(List(Preview.unit.onRun(run).score(Scores.high(context))))
  }
}

object ToggleDesktopIconsCommand extends CommandPlugin[ToggleDesktopIconsCommand] {
  implicit val decoder: Decoder[ToggleDesktopIconsCommand] = Decoder.const(ToggleDesktopIconsCommand())
}

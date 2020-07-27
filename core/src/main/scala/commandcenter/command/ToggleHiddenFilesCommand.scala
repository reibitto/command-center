package commandcenter.command

import commandcenter.CommandContext
import commandcenter.util.OS
import io.circe.Decoder
import zio.process.{ Command => PCommand }
import zio.{ IO, UIO }

final case class ToggleHiddenFilesCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ToggleHiddenFilesCommand

  val commandNames: List[String] = List("hidden")

  val title: String = "Toggle Hidden Files"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  override def keywordPreview(
    keyword: String,
    context: CommandContext
  ): IO[CommandError, List[PreviewResult[Unit]]] = {
    val run = for {
      showingAll <- PCommand("defaults", "read", "com.apple.finder", "AppleShowAllFiles").string.map(_.trim == "1")
      _          <- PCommand("defaults", "write", "com.apple.finder", "AppleShowAllFiles", "-bool", (!showingAll).toString).exitCode
      _          <- PCommand("killall", "Finder").exitCode
    } yield ()

    UIO(List(Preview.unit.onRun(run).score(Scores.high(context))))
  }
}

object ToggleHiddenFilesCommand extends CommandPlugin[ToggleHiddenFilesCommand] {
  implicit val decoder: Decoder[ToggleHiddenFilesCommand] = Decoder.const(ToggleHiddenFilesCommand())
}

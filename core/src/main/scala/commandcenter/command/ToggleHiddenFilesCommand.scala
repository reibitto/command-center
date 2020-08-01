package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.util.OS
import io.circe.Decoder
import zio.ZIO
import zio.process.{ Command => PCommand }

final case class ToggleHiddenFilesCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ToggleHiddenFilesCommand

  val commandNames: List[String] = List("hidden")

  val title: String = "Toggle Hidden Files"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield {
      val run = for {
        showingAll <- PCommand("defaults", "read", "com.apple.finder", "AppleShowAllFiles").string.map(_.trim == "1")
        _          <- PCommand(
                        "defaults",
                        "write",
                        "com.apple.finder",
                        "AppleShowAllFiles",
                        "-bool",
                        (!showingAll).toString
                      ).exitCode
        _          <- PCommand("killall", "Finder").exitCode
      } yield ()

      List(Preview.unit.onRun(run).score(Scores.high(input.context)))
    }
}

object ToggleHiddenFilesCommand extends CommandPlugin[ToggleHiddenFilesCommand] {
  implicit val decoder: Decoder[ToggleHiddenFilesCommand] = Decoder.const(ToggleHiddenFilesCommand())
}

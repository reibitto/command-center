package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.util.OS
import zio.process.{ Command => PCommand }
import zio.{ TaskManaged, ZIO, ZManaged }

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
  def make(config: Config): TaskManaged[ToggleHiddenFilesCommand] = ZManaged.succeed(ToggleHiddenFilesCommand())
}

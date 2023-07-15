package commandcenter.command

import com.typesafe.config.Config
import commandcenter.util.OS
import commandcenter.CCRuntime.Env
import zio.{IO, ZIO}
import zio.process.{Command as PCommand, CommandError as PCommandError}

final case class ToggleHiddenFilesCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ToggleHiddenFilesCommand
  val title: String = "Toggle Hidden Files"

  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield {
      val run = OS.os match {
        case OS.MacOS => runMacOS
        case _        => runWindows
      }

      PreviewResults.one(Preview.unit.onRun(run).score(Scores.veryHigh(input.context)))
    }

  private def runMacOS: ZIO[Any, PCommandError, Unit] =
    for {
      showingAll <- PCommand("defaults", "read", "com.apple.finder", "AppleShowAllFiles").string.map(_.trim == "1")
      _ <- PCommand(
             "defaults",
             "write",
             "com.apple.finder",
             "AppleShowAllFiles",
             "-bool",
             (!showingAll).toString
           ).exitCode
      _ <- PCommand("killall", "Finder").exitCode
    } yield ()

  private def runWindows: ZIO[Any, PCommandError, Unit] =
    for {
      showingAllFlag <-
        PCommand(
          "powershell",
          "(Get-ItemProperty Registry::HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced -Name Hidden).Hidden"
        ).string.map(_.trim.toInt)
      _ <- PCommand(
             "reg",
             "add",
             "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced",
             "/v",
             "Hidden",
             "/t",
             "REG_DWORD",
             "/d",
             (1 - showingAllFlag).toString,
             "/f"
           ).exitCode
    } yield ()
}

object ToggleHiddenFilesCommand extends CommandPlugin[ToggleHiddenFilesCommand] {

  def make(config: Config): IO[CommandPluginError, ToggleHiddenFilesCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield ToggleHiddenFilesCommand(commandNames.getOrElse(List("hidden")))
}

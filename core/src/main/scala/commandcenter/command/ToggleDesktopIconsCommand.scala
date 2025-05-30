package commandcenter.command

import com.typesafe.config.Config
import commandcenter.util.OS
import commandcenter.CCRuntime.Env
import zio.*
import zio.process.Command as PCommand

final case class ToggleDesktopIconsCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ToggleDesktopIconsCommand
  val title: String = "Toggle Desktop Icons"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield {
      val run = for {
        showingIcons <- PCommand("defaults", "read", "com.apple.finder", "CreateDesktop").string.map(_.trim == "1")
        _            <-
          PCommand("defaults", "write", "com.apple.finder", "CreateDesktop", "-bool", (!showingIcons).toString).exitCode
        _ <- PCommand("killall", "Finder").exitCode
      } yield ()

      PreviewResults.one(Preview.unit.onRun(run).score(Scores.veryHigh(input.context)))
    }
}

object ToggleDesktopIconsCommand extends CommandPlugin[ToggleDesktopIconsCommand] {

  def make(config: Config): IO[CommandPluginError, ToggleDesktopIconsCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield ToggleDesktopIconsCommand(commandNames.getOrElse(List("icons")))
}

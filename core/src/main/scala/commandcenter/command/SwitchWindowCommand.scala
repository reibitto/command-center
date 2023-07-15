package commandcenter.command

import com.typesafe.config.Config
import commandcenter.util.{OS, WindowManager}
import commandcenter.CCRuntime.Env
import zio.{IO, ZIO}

final case class SwitchWindowCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.SwitchWindowCommand
  val title: String = "Switch Window"

  // TODO: Support macOS and Linux too
  override val supportedOS: Set[OS] = Set(OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      // TODO: Consider adding more info than just the title. Like "File Explorer" and so on.
      windows <- WindowManager.topLevelWindows.mapBoth(
                   CommandError.UnexpectedError(this),
                   _.tail.filter(_.title.contains(input.rest))
                 )
    } yield PreviewResults.fromIterable(windows.map { w =>
      Preview.unit
        .onRun(WindowManager.giveWindowFocus(w.window))
        .score(Scores.veryHigh(input.context))
        .renderedAnsi(fansi.Color.Cyan(w.title))
    })
}

object SwitchWindowCommand extends CommandPlugin[SwitchWindowCommand] {

  def make(config: Config): IO[CommandPluginError, SwitchWindowCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield SwitchWindowCommand(commandNames.getOrElse(List("window", "w")))
}

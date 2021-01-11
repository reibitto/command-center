package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.util.{ OS, WindowManager }
import zio.{ TaskManaged, ZIO, ZManaged }

final case class SwitchWindowCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.SwitchWindowCommand
  val title: String            = "Switch Window"

  // TODO: Support macOS and Linux too
  override val supportedOS: Set[OS] = Set(OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input   <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      // TODO: Consider adding more info than just the title. Like "File Explorer" and so on.
      windows <- WindowManager.topLevelWindows.bimap(
                   CommandError.UnexpectedException,
                   _.tail.filter(_.title.contains(input.rest))
                 )
    } yield windows.map { w =>
      Preview.unit
        .onRun(WindowManager.giveWindowFocus(w.window))
        .score(Scores.high(input.context))
        .view(fansi.Color.Cyan(w.title))
    }
}

object SwitchWindowCommand extends CommandPlugin[SwitchWindowCommand] {
  def make(config: Config): TaskManaged[SwitchWindowCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield SwitchWindowCommand(commandNames.getOrElse(List("window", "w")))
    )
}

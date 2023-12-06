package commandcenter.command

import com.typesafe.config.Config
import commandcenter.event.KeyboardShortcut
import commandcenter.util.OS
import commandcenter.util.PowerShellScript
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.*

final case class ScreensaverCommand(
    commandNames: List[String],
    override val shortcuts: Set[KeyboardShortcut] = Set.empty
) extends Command[Unit] {
  val commandType: CommandType = CommandType.ScreensaverCommand
  val title: String = "Screensaver"

  // TODO: Support all OSes
  override val supportedOS: Set[OS] = Set(OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] = {
    val inputOpt = searchInput.asKeyword

    for {
      _ <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield {
      val run =
        PowerShellScript.executeCommand("& (Get-ItemProperty ‘HKCU:Control Panel\\Desktop’).{SCRNSAVE.EXE}").unit

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(inputOpt.fold(Scores.hide)(input => Scores.veryHigh(input.context)))
          .rendered(Renderer.renderDefault(title, ""))
      )
    }
  }

}

object ScreensaverCommand extends CommandPlugin[ScreensaverCommand] {

  def make(config: Config): IO[CommandPluginError, ScreensaverCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
      shortcuts    <- config.getZIO[Option[Set[KeyboardShortcut]]]("shortcuts")
    } yield ScreensaverCommand(
      commandNames.getOrElse(List("ss", "screensaver")),
      shortcuts.getOrElse(Set.empty)
    )

}

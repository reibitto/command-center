package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.util.OS
import io.circe.Decoder
import zio.ZIO
import zio.process.{ Command => PCommand }

final case class ToggleDesktopIconsCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ToggleDesktopIconsCommand

  val commandNames: List[String] = List("icons")

  val title: String = "Toggle Desktop Icons"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
    } yield {
      val run = for {
        showingIcons <- PCommand("defaults", "read", "com.apple.finder", "CreateDesktop").string.map(_.trim == "1")
        _            <- PCommand("defaults", "write", "com.apple.finder", "CreateDesktop", "-bool", (!showingIcons).toString).exitCode
        _            <- PCommand("killall", "Finder").exitCode
      } yield ()

      List(Preview.unit.onRun(run).score(Scores.high(input.context)))
    }
}

object ToggleDesktopIconsCommand extends CommandPlugin[ToggleDesktopIconsCommand] {
  implicit val decoder: Decoder[ToggleDesktopIconsCommand] = Decoder.const(ToggleDesktopIconsCommand())
}

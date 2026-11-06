package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.Str
import zio.*

final case class OpacityCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.OpacityCommand
  val title: String = "Set Opacity"

  val opacity = Opts.argument[Float]("opacity").validate("Opacity must be between 0.0-1.0")(o => o >= 0.0f && o <= 1.0f)

  val opacityCommand = decline.Command("opacity", title)(opacity)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      _     <- ZIO.fail(CommandError.NotApplicable).unlessZIO(searchInput.context.terminal.isOpacitySupported)
      input <- searchInput.args
      parsed = opacityCommand.parse(input.args)
      message = parsed.fold(HelpMessage.formatted, o => Str(s"Set opacity to $o"))
      currentOpacity <- input.context.terminal.opacity.mapError(CommandError.UnexpectedError(this))
    } yield {
      val run = ZIO.fromEither(parsed).orElseFail(RunError.Ignore).flatMap(input.context.terminal.setOpacity)

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.veryHigh(input.context))
          .rendered(Renderer.renderDefault(s"$title (current: $currentOpacity)", message))
      )
    }
}

object OpacityCommand extends CommandPlugin[OpacityCommand] {

  def make(config: Config): IO[CommandPluginError, OpacityCommand] =
    commandNames(config, "opacity").map(OpacityCommand(_))
}

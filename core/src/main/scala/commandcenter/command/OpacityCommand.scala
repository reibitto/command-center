package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.view.Renderer
import zio.{ Managed, ZIO }

final case class OpacityCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.OpacityCommand
  val title: String            = "Set Opacity"

  val opacity = Opts.argument[Float]("opacity").validate("Opacity must be between 0.0-1.0")(o => o >= 0.0f && o <= 1.0f)

  val opacityCommand = decline.Command("opacity", title)(opacity)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      _              <- ZIO.fail(CommandError.NotApplicable).unlessM(searchInput.context.terminal.isOpacitySupported)
      input          <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed          = opacityCommand.parse(input.args)
      message        <- ZIO
                          .fromEither(parsed)
                          .fold(HelpMessage.formatted, o => fansi.Str(s"Set opacity to $o"))
      currentOpacity <- input.context.terminal.opacity.mapError(CommandError.UnexpectedException)
    } yield {
      val run = for {
        opacity <- ZIO.fromEither(parsed).mapError(RunError.CliError)
        _       <- input.context.terminal.setOpacity(opacity)
      } yield ()

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.high(input.context))
          .rendered(Renderer.renderDefault(s"$title (current: $currentOpacity)", message))
      )
    }
}

object OpacityCommand extends CommandPlugin[OpacityCommand] {
  def make(config: Config): Managed[CommandPluginError, OpacityCommand] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
    } yield OpacityCommand(commandNames.getOrElse(List("opacity")))
}

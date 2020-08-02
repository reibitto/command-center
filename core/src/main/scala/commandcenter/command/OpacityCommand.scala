package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import commandcenter.CCRuntime.Env
import commandcenter.TerminalType
import commandcenter.util.GraphicsUtil
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO
import zio.blocking._

final case class OpacityCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.OpacityCommand

  val commandNames: List[String] = List("opacity")

  val title: String = "Set Opacity"

  val opacity = Opts.argument[Float]("opacity").validate("Opacity must be between 0.0-1.0")(o => o >= 0.0f && o <= 1.0f)

  val opacityCommand = decline.Command("opacity", title)(opacity)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] = {
    val isSwingTerminal = searchInput.context.terminal.terminalType == TerminalType.Swing
    val applicable      = GraphicsUtil.isOpacitySupported.map(isSwingTerminal && _)
    for {
      _              <- ZIO.fail(CommandError.NotApplicable).unlessM(applicable)
      input          <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed          = opacityCommand.parse(input.args)
      message        <- ZIO
                          .fromEither(parsed)
                          .fold(HelpMessage.formatted, o => fansi.Str(s"Set opacity to ${o}"))
      currentOpacity <- input.context.terminal.opacity.mapError(CommandError.UnexpectedException)
    } yield {
      val run = ZIO.fromEither(parsed).flatMap(o => input.context.terminal.setOpacity(o))

      List(
        Preview.unit
          .onRun(run.ignore)
          .score(Scores.high(input.context))
          .view(DefaultView(s"$title (current: $currentOpacity)", message))
      )
    }
  }
}

object OpacityCommand extends CommandPlugin[OpacityCommand] {
  implicit val decoder: Decoder[OpacityCommand] = Decoder.const(OpacityCommand())
}

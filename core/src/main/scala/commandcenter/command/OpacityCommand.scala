package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ UIO, ZIO }

final case class OpacityCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.OpacityCommand

  val commandNames: List[String] = List("opacity")

  val title: String = "Set Opacity"

  val opacity = Opts.argument[Float]("opacity").validate("Opacity must be between 0.0-1.0")(o => o >= 0.0f && o <= 1.0f)

  val opacityCommand = decline.Command("opacity", title)(opacity)

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): ZIO[Env, CommandError, List[PreviewResult[Unit]]] = {
    val parsed = opacityCommand.parse(args)

    for {
      message <- ZIO
                  .fromEither(parsed)
                  .foldM(
                    help => UIO(HelpMessage.formatted(help)),
                    o => UIO(fansi.Str(s"Set opacity to ${o}"))
                  )
      currentOpacity <- context.terminal.opacity.mapError(CommandError.UnexpectedException)
    } yield {
      val run = ZIO.fromEither(parsed).flatMap(o => context.terminal.setOpacity(o))

      List(
        Preview.unit
          .onRun(run.ignore)
          .score(Scores.high(context))
          .view(DefaultView(s"$title (current: $currentOpacity)", message))
      )
    }
  }
}

object OpacityCommand extends CommandPlugin[OpacityCommand] {
  implicit val decoder: Decoder[OpacityCommand] = Decoder.const(OpacityCommand())
}

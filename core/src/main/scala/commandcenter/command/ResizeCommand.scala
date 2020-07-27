package commandcenter.command

import cats.implicits._
import com.monovore.decline
import com.monovore.decline.Opts
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ UIO, ZIO }

final case class ResizeCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ResizeCommand

  val commandNames: List[String] = List("resize", "size")

  val title: String = "Resize Window"

  val width  = Opts.argument[Int]("width").validate("Width must be greater than 0")(_ > 0)
  val height = Opts.argument[Int]("height").validate("Height must be greater than 0")(_ > 0)

  val resizeCommand = decline.Command("resize", title)((width, height).tupled)

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): ZIO[Env, CommandError, List[PreviewResult[Unit]]] = {
    val parsed = resizeCommand.parse(args)

    for {
      message <- ZIO
                  .fromEither(parsed)
                  .foldM(
                    help => UIO(HelpMessage.formatted(help)),
                    { case (w, h) => UIO(fansi.Str(s"Set window size to width: $w, maxHeight: $h)")) }
                  )
    } yield {
      val run = ZIO.fromEither(parsed).flatMap { case (w, h) => context.terminal.setSize(w, h) }

      List(
        Preview.unit
          .onRun(run.ignore)
          .score(Scores.high(context))
          .view(DefaultView(title, message))
      )
    }
  }
}

object ResizeCommand extends CommandPlugin[ResizeCommand] {
  implicit val decoder: Decoder[ResizeCommand] = Decoder.const(ResizeCommand())
}

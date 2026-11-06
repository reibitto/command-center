package commandcenter.command

import cats.implicits.*
import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.Str
import zio.*

final case class ResizeCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ResizeCommand
  val title: String = "Resize Window"

  val width = Opts.argument[Int]("width").validate("Width must be greater than 0")(_ > 0)
  val height = Opts.argument[Int]("height").validate("Height must be greater than 0")(_ > 0)

  val resizeCommand = decline.Command("resize", title)((width, height).tupled)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- searchInput.args
      parsed = resizeCommand.parse(input.args)
      message = parsed.fold(
                  HelpMessage.formatted,
                  { case (w, h) =>
                    Str(s"Set window size to width: $w, height: $h")
                  }
                )
    } yield {
      val run = ZIO.fromEither(parsed).orElseFail(RunError.Ignore).flatMap { case (w, h) =>
        input.context.terminal.setSize(w, h)
      }

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.veryHigh(input.context))
          .rendered(Renderer.renderDefault(title, message))
      )
    }
}

object ResizeCommand extends CommandPlugin[ResizeCommand] {

  def make(config: Config): IO[CommandPluginError, ResizeCommand] =
    commandNames(config, "resize", "size").map(ResizeCommand(_))
}

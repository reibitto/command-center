package commandcenter.command

import cats.implicits._
import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.view.DefaultView
import zio.{ TaskManaged, ZIO, ZManaged }

final case class ResizeCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ResizeCommand
  val title: String            = "Resize Window"

  val width  = Opts.argument[Int]("width").validate("Width must be greater than 0")(_ > 0)
  val height = Opts.argument[Int]("height").validate("Height must be greater than 0")(_ > 0)

  val resizeCommand = decline.Command("resize", title)((width, height).tupled)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input   <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed   = resizeCommand.parse(input.args)
      message <- ZIO
                   .fromEither(parsed)
                   .fold(
                     HelpMessage.formatted,
                     {
                       case (w, h) => fansi.Str(s"Set window size to width: $w, maxHeight: $h)")
                     }
                   )
    } yield {
      val run = for {
        (w, h) <- ZIO.fromEither(parsed).mapError(RunError.CliError)
        _      <- input.context.terminal.setSize(w, h)
      } yield ()

      List(
        Preview.unit
          .onRun(run.orDie)
          .score(Scores.high(input.context))
          .view(DefaultView(title, message))
      )
    }
}

object ResizeCommand extends CommandPlugin[ResizeCommand] {
  def make(config: Config): TaskManaged[ResizeCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield ResizeCommand(commandNames.getOrElse(List("resize", "size")))
    )
}

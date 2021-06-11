package commandcenter.strokeorder

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command._
import commandcenter.tools.Tools
import commandcenter.view.{ Rendered, Style, StyledText }
import zio.{ TaskManaged, ZIO, ZManaged }

final case class StrokeOrderCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.External(getClass.getCanonicalName)
  val title: String            = "Stroke Order"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty)).orElseFail(CommandError.NotApplicable)
    } yield PreviewResults.one(
      Preview.unit
        .score(Scores.high(searchInput.context))
        .onRun(Tools.setClipboard(input.rest))
        .renderFn { _ =>
          Rendered.Styled(
            Vector(
              StyledText(input.rest, Set(Style.FontFamily("KanjiStrokeOrders"), Style.FontSize(175)))
            )
          )
        }
    )
}

object StrokeOrderCommand extends CommandPlugin[StrokeOrderCommand] {
  def make(config: Config): TaskManaged[StrokeOrderCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield StrokeOrderCommand(commandNames.getOrElse(List("stroke", "strokeorder")))
    )
}

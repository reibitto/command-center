package commandcenter.command

import cats.syntax.apply.*
import com.monovore.decline
import com.monovore.decline.{Help, Opts}
import com.typesafe.config.Config
import commandcenter.tools.Tools
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.{Managed, ZIO}

import scala.util.Try

final case class RadixCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.RadixCommand
  val title: String = "Convert base"

  val fromRadixOpt = Opts.option[Int]("from", "Radix to convert from", "f").orNone
  val toRadixOpt = Opts.option[Int]("to", "Radix to convert to", "t").orNone
  val numberArg = Opts.argument[String]("number")

  val radixCommand = decline.Command("radix", title)((fromRadixOpt, toRadixOpt, numberArg).tupled)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed = radixCommand.parse(input.args)
      preview <- ZIO
                   .fromEither(parsed)
                   .fold(
                     h => Preview.help(h).score(Scores.high(input.context)),
                     { case (fromRadixOpt, toRadixOpt, number) =>
                       val fromRadix = fromRadixOpt.getOrElse(10)
                       val toRadix = toRadixOpt.getOrElse(10)

                       Try {
                         val n = java.lang.Long.valueOf(number, fromRadix)
                         val formatted = java.lang.Long.toString(n, toRadix)
                         val message = fansi.Str(s"$formatted")

                         Preview.unit
                           .score(Scores.high(input.context))
                           .onRun(Tools.setClipboard(message.plainText))
                           .rendered(Renderer.renderDefault(title, message))
                       }.getOrElse {
                         Preview.help(Help.fromCommand(radixCommand)).score(Scores.high(input.context))
                       }
                     }
                   )
    } yield PreviewResults.one(preview)

}

object RadixCommand extends CommandPlugin[RadixCommand] {

  def make(config: Config): Managed[CommandPluginError, RadixCommand] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
    } yield RadixCommand(commandNames.getOrElse(List("radix", "base")))
}

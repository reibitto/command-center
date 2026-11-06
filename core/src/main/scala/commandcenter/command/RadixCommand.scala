package commandcenter.command

import cats.syntax.apply.*
import com.monovore.decline
import com.monovore.decline.{Help, Opts}
import com.typesafe.config.Config
import commandcenter.tools.Tools
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import fansi.Str
import zio.*

import scala.util.matching.Regex
import scala.util.Try

final case class RadixCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.RadixCommand
  val title: String = "Convert base"

  // BigInt silently falls back to base 10 for radixes outside 2-36, so they're rejected up front
  private def validRadix(opt: Opts[Int]): Opts[Int] =
    opt.validate(s"Radix must be between ${Character.MIN_RADIX} and ${Character.MAX_RADIX}")(r =>
      r >= Character.MIN_RADIX && r <= Character.MAX_RADIX
    )

  val fromRadixOpt = validRadix(Opts.option[Int]("from", "Radix to convert from", "f")).orNone
  val toRadixOpt = validRadix(Opts.option[Int]("to", "Radix to convert to", "t")).orNone
  val numberArg = Opts.argument[String]("number")

  val radixCommand = decline.Command("radix", title)((fromRadixOpt, toRadixOpt, numberArg).tupled)

  val hexRegex: Regex = "0[xX]([0-9a-fA-F]+)".r

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    ZIO
      .succeed(detectAndPreviewHex(searchInput))
      .someOrElseZIO(previewRadixCommand(searchInput))

  private def previewRadixCommand(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- searchInput.args
      parsed = radixCommand.parse(input.args)
      preview = parsed.fold(
                  h => Preview.help(h).score(Scores.veryHigh(input.context)),
                  { case (fromRadixOpt, toRadixOpt, numberAsString) =>
                    val fromRadix = fromRadixOpt.getOrElse(10)
                    val toRadix = toRadixOpt.getOrElse(10)

                    Try {
                      convertNumber(numberAsString, fromRadix, toRadix, input.context)
                    }.getOrElse {
                      Preview.help(Help.fromCommand(radixCommand)).score(Scores.veryHigh(input.context))
                    }
                  }
                )
    } yield PreviewResults.one(preview)

  private def detectAndPreviewHex(searchInput: SearchInput): Option[PreviewResults[Unit]] =
    searchInput.input match {
      case hexRegex(n) =>
        Some(
          PreviewResults.multiple(
            convertNumber(n, 16, 10, searchInput.context),
            convertNumber(n, 16, 2, searchInput.context)
          )
        )

      case _ => None
    }

  private def convertNumber(
      numberAsString: String,
      fromRadix: Int,
      toRadix: Int,
      context: CommandContext
  ): PreviewResult[Unit] = {
    // BigInt so values beyond the Long range (e.g. 0xFFFFFFFFFFFFFFFF) convert instead of throwing
    val message = Str(BigInt(numberAsString, fromRadix).toString(toRadix))

    Preview.unit
      .score(Scores.veryHigh(context))
      .onRun(Tools.setClipboard(message.plainText))
      .rendered(Renderer.renderDefault(s"Convert base $fromRadix to $toRadix", message))
  }

}

object RadixCommand extends CommandPlugin[RadixCommand] {

  def make(config: Config): IO[CommandPluginError, RadixCommand] =
    commandNames(config, "radix", "base").map(RadixCommand(_))
}

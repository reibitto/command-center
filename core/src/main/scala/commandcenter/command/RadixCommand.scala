package commandcenter.command

import cats.syntax.apply._
import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools
import commandcenter.view.DefaultView
import zio.{ TaskManaged, ZIO, ZManaged }

final case class RadixCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.RadixCommand
  val title: String            = "Convert base"

  val fromRadixOpt = Opts.option[Int]("from", "Radix to convert from", "f").orNone
  val toRadixOpt   = Opts.option[Int]("to", "Radix to convert to", "t").orNone
  val numberArg    = Opts.argument[String]("number")

  val radixCommand = decline.Command("radix", title)((fromRadixOpt, toRadixOpt, numberArg).tupled)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input   <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed   = radixCommand.parse(input.args)
      message <- ZIO
                   .fromEither(parsed)
                   .fold(
                     HelpMessage.formatted,
                     {
                       case (fromRadixOpt, toRadixOpt, number) =>
                         val fromRadix = fromRadixOpt.getOrElse(10)
                         val toRadix   = toRadixOpt.getOrElse(10)
                         val n         = java.lang.Long.valueOf(number, fromRadix)
                         val formatted = java.lang.Long.toString(n, toRadix)

                         fansi.Str(s"$formatted")
                     }
                   )
    } yield List(
      Preview.unit
        .score(Scores.high(input.context))
        .onRun(tools.setClipboard(message.plainText))
        .view(DefaultView(title, message))
    )

}

object RadixCommand extends CommandPlugin[RadixCommand] {
  def make(config: Config): TaskManaged[RadixCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield RadixCommand(commandNames.getOrElse(List("radix", "base")))
    )
}

package commandcenter.command

import com.typesafe.config.Config
import commandcenter.tools.Tools
import commandcenter.util.OS
import commandcenter.CCRuntime.Env
import zio.*
import zio.process.Command as PCommand

final case class ExternalIPCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.ExternalIPCommand
  val title: String = "External IP"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input      <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      externalIP <- getExternalIP
    } yield PreviewResults.one(
      Preview(externalIP)
        .score(Scores.veryHigh(input.context))
        .onRun(Tools.setClipboard(externalIP))
    )

  private def getExternalIP: ZIO[Any, CommandError, String] =
    OS.os match {
      case OS.Windows =>
        val prefix = "Address:"
        (for {
          lines <- PCommand("nslookup", "myip.opendns.com", "resolver1.opendns.com").lines
        } yield lines.filter(_.startsWith("Address:")).drop(1).headOption.map { a =>
          a.drop(prefix.length).trim
        }).mapError(CommandError.UnexpectedError(this))
          .someOrFail(CommandError.UnexpectedError.fromMessage(this)("Could not parse nslookup results"))
      case _ =>
        PCommand("dig", "+short", "myip.opendns.com", "@resolver1.opendns.com").string
          .mapError(CommandError.UnexpectedError(this))
          .map(_.trim)
    }
}

object ExternalIPCommand extends CommandPlugin[ExternalIPCommand] {

  def make(config: Config): IO[CommandPluginError, ExternalIPCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield ExternalIPCommand(commandNames.getOrElse(List("externalip")))
}

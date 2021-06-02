package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools
import commandcenter.util.OS
import zio.blocking.Blocking
import zio.process.{ Command => PCommand }
import zio.{ TaskManaged, ZIO, ZManaged }

final case class ExternalIPCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.ExternalIPCommand
  val title: String            = "External IP"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input      <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      externalIP <- getExternalIP
    } yield PreviewResults.one(
      Preview(externalIP)
        .score(Scores.high(input.context))
        .onRun(tools.setClipboard(externalIP))
    )

  private def getExternalIP: ZIO[Blocking, CommandError, String] =
    OS.os match {
      case OS.Windows =>
        val prefix = "Address:"
        (for {
          lines <- PCommand("nslookup", "myip.opendns.com", "resolver1.opendns.com").lines
        } yield lines.filter(_.startsWith("Address:")).drop(1).headOption.map { a =>
          a.drop(prefix.length).trim
        }).mapError(CommandError.UnexpectedException)
          .someOrFail(CommandError.InternalError("Could not parse nslookup results"))
      case _          =>
        PCommand("dig", "+short", "myip.opendns.com", "@resolver1.opendns.com").string
          .bimap(CommandError.UnexpectedException, _.trim)
    }
}

object ExternalIPCommand extends CommandPlugin[ExternalIPCommand] {
  def make(config: Config): TaskManaged[ExternalIPCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield ExternalIPCommand(commandNames.getOrElse(List("externalip")))
    )
}

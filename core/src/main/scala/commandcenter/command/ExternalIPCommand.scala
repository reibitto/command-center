package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools
import commandcenter.util.OS
import sttp.client._
import sttp.client.httpclient.zio.SttpClient
import zio.blocking.Blocking
import zio.process.{ Command => PCommand }
import zio.{ TaskManaged, ZIO, ZManaged }

final case class ExternalIPCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.ExternalIPCommand
  val title: String            = "External IP"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input      <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      externalIP <- getExternalIP
    } yield List(
      Preview(externalIP)
        .score(Scores.high(input.context))
        .onRun(tools.setClipboard(externalIP))
    )

  private def getExternalIP: ZIO[SttpClient with Blocking, CommandError, String] =
    OS.os match {
      case OS.Windows =>
        val request = basicRequest.get(uri"https://api.ipify.org").response(asString)
        SttpClient
          .send(request)
          .bimap(CommandError.UnexpectedException, _.body)
          .rightOrFailWith(CommandError.InternalError)
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

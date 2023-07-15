package commandcenter.command

import com.typesafe.config.Config
import commandcenter.tools.Tools
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.{IO, ZIO}
import zio.ZIO.attemptBlocking

import java.net.{Inet4Address, NetworkInterface}
import scala.jdk.CollectionConverters.*

final case class LocalIPCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.LocalIPCommand
  val title: String = "Local IP"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      localIps <- attemptBlocking {
                    val interfaces = NetworkInterface.getNetworkInterfaces.asScala.toList
                    interfaces
                      .filter(interface => !interface.isLoopback && !interface.isVirtual && interface.isUp)
                      .flatMap { interface =>
                        interface.getInetAddresses.asScala.collect { case address: Inet4Address =>
                          interface.getDisplayName -> address.getHostAddress
                        }
                      }
                  }.mapError(CommandError.UnexpectedError(this))
    } yield PreviewResults.fromIterable(localIps.map { case (interfaceName, localIp) =>
      Preview(localIp)
        .onRun(Tools.setClipboard(localIp))
        .score(Scores.veryHigh(input.context))
        .rendered(
          Renderer.renderDefault(title, fansi.Str(interfaceName) ++ fansi.Str(": ") ++ fansi.Color.Magenta(localIp))
        )
    })
}

object LocalIPCommand extends CommandPlugin[LocalIPCommand] {

  def make(config: Config): IO[CommandPluginError, LocalIPCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield LocalIPCommand(commandNames.getOrElse(List("localip")))
}

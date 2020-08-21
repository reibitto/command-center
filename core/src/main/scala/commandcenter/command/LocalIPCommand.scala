package commandcenter.command

import java.net.{ Inet4Address, NetworkInterface }

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools
import commandcenter.view.DefaultView
import zio.blocking._
import zio.{ TaskManaged, ZIO, ZManaged }

import scala.jdk.CollectionConverters._

final case class LocalIPCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.LocalIPCommand
  val title: String            = "Local IP"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input    <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      localIps <- effectBlocking {
                    val interfaces = NetworkInterface.getNetworkInterfaces.asScala.toList
                    interfaces
                      .filter(interface => !interface.isLoopback && !interface.isVirtual && interface.isUp)
                      .flatMap { interface =>
                        interface.getInetAddresses.asScala.collect {
                          case address: Inet4Address => interface.getDisplayName -> address.getHostAddress
                        }
                      }
                  }.mapError(CommandError.UnexpectedException)
    } yield localIps.map {
      case (interfaceName, localIp) =>
        Preview(localIp)
          .onRun(tools.setClipboard(localIp))
          .score(Scores.high(input.context))
          .view(DefaultView(title, fansi.Str(interfaceName) ++ fansi.Str(": ") ++ fansi.Color.Magenta(localIp)))
    }
}

object LocalIPCommand extends CommandPlugin[LocalIPCommand] {
  def make(config: Config): TaskManaged[LocalIPCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield LocalIPCommand(commandNames.getOrElse(List("localip")))
    )
}

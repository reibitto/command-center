package commandcenter.command

import java.net.{ Inet4Address, NetworkInterface }

import commandcenter.CCRuntime.Env
import commandcenter.tools
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO
import zio.blocking._

import scala.jdk.CollectionConverters._

final case class LocalIPCommand() extends Command[String] {
  val commandType: CommandType = CommandType.LocalIPCommand

  val commandNames: List[String] = List("localip")

  val title: String = "Local IP"

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
  implicit val decoder: Decoder[LocalIPCommand] = Decoder.const(LocalIPCommand())
}

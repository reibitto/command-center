package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.tools
import commandcenter.util.OS
import zio.process.{ Command => PCommand }
import zio.{ TaskManaged, ZIO, ZManaged }

final case class ExternalIPCommand() extends Command[String] {
  val commandType: CommandType = CommandType.ExternalIPCommand

  val commandNames: List[String] = List("externalip")

  val title: String = "External IP"

  // TODO: Also support Windows (nslookup?). If there's no good solution, making an api.ipify.org request could work too.
  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Linux)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input      <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      externalIP <- PCommand("dig", "+short", "myip.opendns.com", "@resolver1.opendns.com").string
                      .bimap(CommandError.UnexpectedException, _.trim)
    } yield List(
      Preview(externalIP)
        .score(Scores.high(input.context))
        .onRun(tools.setClipboard(externalIP))
    )
}

object ExternalIPCommand extends CommandPlugin[ExternalIPCommand] {
  def make(config: Config): TaskManaged[ExternalIPCommand] = ZManaged.succeed(ExternalIPCommand())
}

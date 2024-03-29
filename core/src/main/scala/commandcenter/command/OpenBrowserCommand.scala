package commandcenter.command

import com.typesafe.config.Config
import commandcenter.command.CommandError.*
import commandcenter.util.ProcessUtil
import commandcenter.CCRuntime.Env
import zio.*

final case class OpenBrowserCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.OpenBrowserCommand

  val commandNames: List[String] = List.empty

  val title: String = "Open in Browser"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] = {
    val input = searchInput.input
    val startsWith = input.startsWith("http://") || input.startsWith("https://")

    // TODO: also check endsWith TLD + URL.isValid

    if (startsWith)
      ZIO.succeed(PreviewResults.one(Preview.unit.onRun(ProcessUtil.openBrowser(input))))
    else
      ZIO.fail(NotApplicable)
  }
}

object OpenBrowserCommand extends CommandPlugin[OpenBrowserCommand] {
  def make(config: Config): UIO[OpenBrowserCommand] = ZIO.succeed(OpenBrowserCommand())
}

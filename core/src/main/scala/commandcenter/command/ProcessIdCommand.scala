package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.tools
import commandcenter.util.OS
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.ZIO
import zio.process.{ Command => PCommand }

import scala.util.matching.Regex

final case class ProcessIdCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ProcessIdCommand

  val commandNames: List[String] = List("pid", "process")

  val title: String = "Process ID"

  val visibleProcessRegex: Regex = "(ASN:.+?)-\"(.+?)\"".r

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input        <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      stringOutput <- PCommand("lsappinfo", "visibleProcessList").string.mapError(CommandError.UnexpectedException)
      searchString  = input.rest.toLowerCase
      processInfo   = visibleProcessRegex
                        .findAllMatchIn(stringOutput)
                        .map { m =>
                          val asn         = m.group(1)
                          val processName = m.group(2).replace("_", " ")
                          (asn, processName)
                        }
                        .toList
                        .filter {
                          case (_, processName) =>
                            processName.toLowerCase.contains(searchString)
                        }
    } yield processInfo.map {
      case (asn, processName) =>
        val run = for {
          pidOutput <- PCommand("lsappinfo", "info", "-only", "pid", asn).string.map(_.trim)
          pid       <- ZIO
                         .fromOption(pidOutput.split('=').lift(1).map(_.trim))
                         .orElseFail(RunError.InternalError("Parsing PID failed"))
          _         <- tools.setClipboard(pid)
        } yield ()

        Preview.unit
          .onRun(run)
          .view(DefaultView(processName, "Copy PID to clipboard"))
          .score(Scores.high(input.context))
    }
}

object ProcessIdCommand extends CommandPlugin[ProcessIdCommand] {
  implicit val decoder: Decoder[ProcessIdCommand] = Decoder.const(ProcessIdCommand())

  final case class ProcessInfo(pid: Long, name: String)
}

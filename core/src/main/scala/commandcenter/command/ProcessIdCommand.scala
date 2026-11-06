package commandcenter.command

import com.typesafe.config.Config
import commandcenter.command.ProcessIdCommand.ProcessInfo
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import fansi.Color
import zio.*

import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

final case class ProcessIdCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ProcessIdCommand
  val title: String = "Process ID"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input     <- searchInput.prefixed
      processes <- ZIO.blocking(
                     ZIO.succeed(
                       ProcessHandle
                         .allProcesses()
                         .iterator()
                         .asScala
                         .toSeq
                         .flatMap { p =>
                           (p.info.command.toScala, p.info.startInstant.toScala) match {
                             case (Some(command), Some(startTime)) =>
                               Some(ProcessInfo(p.pid(), command, startTime))

                             case _ =>
                               None
                           }
                         }
                         .sortBy(_.startTime)(Ordering[Instant].reverse)
                     )
                   )
    } yield PreviewResults.Paginated.fromIterable(
      processes.map { process =>
        val run = Tools.setClipboard(process.pid.toString)

        Preview.unit
          .onRun(run)
          .score(Scores.veryHigh(input.context))
          .renderedAnsi(
            Color.Cyan(process.command) ++ "\n" ++ "Copy PID to clipboard: " ++ Color.Magenta(process.pid.toString)
          )
      },
      initialPageSize = 15,
      morePageSize = 30
    )
}

object ProcessIdCommand extends CommandPlugin[ProcessIdCommand] {

  def make(config: Config): IO[CommandPluginError, ProcessIdCommand] =
    commandNames(config, "pid", "process").map(ProcessIdCommand(_))

  final case class ProcessInfo(pid: Long, command: String, startTime: Instant)
}

package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.config.Decoders.keyboardShortcutDecoder
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts
import commandcenter.util.{ OS, ProcessUtil }
import commandcenter.view.DefaultView
import zio.blocking.Blocking
import zio.logging.log
import zio.process.{ Command => PCommand }
import zio.{ RIO, RManaged, ZIO, ZManaged }

final case class SuspendProcessCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.SuspendProcessCommand
  val title: String            = "Suspend/Resume Process"

  override val supportedOS: Set[OS] = Set(OS.MacOS, OS.Linux)

  val command = decline.Command("suspend", title)(Opts.argument[Long]("pid"))

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input        <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsedCommand = command.parse(input.args)
      message      <- ZIO
                        .fromEither(parsedCommand)
                        .fold(HelpMessage.formatted, p => fansi.Str("PID: ") ++ fansi.Color.Magenta(p.toString))
    } yield {
      val run = for {
        pid         <- ZIO.fromEither(parsedCommand).mapError(RunError.CliError)
        isSuspended <- SuspendProcessCommand.isProcessSuspended(pid)
        _           <- SuspendProcessCommand.setProcessState(!isSuspended, pid)
      } yield ()

      List(
        Preview.unit
          .onRun(run.orDie)
          .score(Scores.high(input.context))
          .view(DefaultView(title, message))
      )
    }
}

object SuspendProcessCommand extends CommandPlugin[SuspendProcessCommand] {
  def make(config: Config): RManaged[Env, SuspendProcessCommand] =
    for {
      commandNames    <- ZManaged.fromEither(config.get[Option[List[String]]]("commandNames"))
      suspendShortcut <- ZManaged.fromEither(config.get[Option[KeyboardShortcut]]("suspendShortcut"))
      _               <- ZIO
                           .foreach(suspendShortcut) { suspendShortcut =>
                             shortcuts.addGlobalShortcut(suspendShortcut)(_ =>
                               (for {
                                 _   <- log.debug("Toggling suspend for frontmost process...")
                                 pid <- SuspendProcessCommand.toggleSuspendFrontProcess
                                 _   <- log.debug(s"Toggled suspend for process $pid")
                               } yield ()).ignore
                             )
                           }
                           .toManaged_
    } yield SuspendProcessCommand(commandNames.getOrElse(List("suspend")))

  def isProcessSuspended(processId: Long): RIO[Blocking, Boolean] = {
    val stateParam = if (OS.os == OS.MacOS) "state=" else "s="

    PCommand("ps", "-o", stateParam, "-p", processId.toString).string.map(_.trim == "T")
  }

  def setProcessState(suspend: Boolean, pid: Long): RIO[Blocking, Unit] = {
    val targetState = if (suspend) "-STOP" else "-CONT"
    PCommand("kill", targetState, pid.toString).exitCode.unit
  }

  def toggleSuspendFrontProcess: RIO[Blocking, Long] =
    for {
      pid         <- ProcessUtil.frontProcessId
      isSuspended <- isProcessSuspended(pid)
      _           <- setProcessState(!isSuspended, pid)
    } yield pid
}

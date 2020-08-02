package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import commandcenter.CCRuntime.Env
import commandcenter.util.{ OS, ProcessUtil }
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.blocking.Blocking
import zio.process.{ Command => PCommand }
import zio.{ RIO, ZIO }

final case class SuspendProcessCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.SuspendProcessCommand

  val commandNames: List[String] = List("suspend")

  val title: String = "Suspend/Resume Process"

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
        pid         <- ZIO.fromEither(parsedCommand)
        isSuspended <- SuspendProcessCommand.isProcessSuspended(pid)
        _           <- SuspendProcessCommand.setProcessState(!isSuspended, pid)
      } yield ()

      List(
        Preview.unit
          .onRun(run.ignore)
          .score(Scores.high(input.context))
          .view(DefaultView(title, message))
      )
    }
}

object SuspendProcessCommand extends CommandPlugin[SuspendProcessCommand] {
  implicit val decoder: Decoder[SuspendProcessCommand] = Decoder.const(SuspendProcessCommand())

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

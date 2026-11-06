package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools
import commandcenter.util.{OS, ProcessUtil, WindowManager}
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.{Color, Str}
import zio.*
import zio.process.Command as PCommand

final case class SuspendProcessCommand(
    commandNames: List[String],
    suspendedPidsRef: Ref.Synchronized[List[Long]]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.SuspendProcessCommand
  val title: String = "Suspend/Resume Process"

  val command = decline.Command("suspend", title)(Opts.argument[Long]("pid"))

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- searchInput.args
      parsedCommand = command.parse(input.args)
      message = parsedCommand.fold(HelpMessage.formatted, p => Str("PID: ") ++ Color.Magenta(p.toString))
    } yield {
      val run = for {
        pid <- ZIO.fromEither(parsedCommand).orElseFail(RunError.Ignore)
        _   <- ZIO.logDebug(s"Toggling suspend for process `$pid`...")
        _   <- SuspendProcessCommand.toggleSuspendProcess(Some(pid), suspendedPidsRef)
      } yield ()

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.veryHigh(input.context))
          .rendered(Renderer.renderDefault(title, message))
      )
    }
}

object SuspendProcessCommand extends CommandPlugin[SuspendProcessCommand] {

  def make(config: Config): ZIO[Env, CommandPluginError, SuspendProcessCommand] =
    for {
      commandNames     <- commandNames(config, "suspend", "pause")
      suspendShortcuts <- config.getZIO("suspendShortcuts", List.empty[KeyboardShortcut])
      suspendedPidsRef <- Ref.Synchronized.make(List.empty[Long])
      _                <- ZIO
             .foreachDiscard(suspendShortcuts) { suspendShortcut =>
               Shortcuts.addGlobalShortcut(suspendShortcut)(_ =>
                 toggleSuspendProcess(None, suspendedPidsRef).tapErrorCause { t =>
                   ZIO.logWarningCause("Error running SuspendProcess command", t)
                 }.ignore
               )
             }
             .mapError(CommandPluginError.UnexpectedException.apply)
    } yield SuspendProcessCommand(commandNames, suspendedPidsRef)

  /** Resumes `pid` if it's suspended, otherwise suspends it. Without a pid (the
    * global shortcut), resumes the most recently suspended process, or suspends
    * the frontmost one if none are suspended.
    */
  def toggleSuspendProcess(pid: Option[Long], suspendedPidsRef: Ref.Synchronized[List[Long]]): RIO[Tools, Unit] =
    suspendedPidsRef.updateZIO { suspended =>
      pid.orElse(suspended.headOption) match {
        case Some(p) if suspended.contains(p) => resume(p).as(suspended.filterNot(_ == p))
        case Some(p)                          => suspend(p).map(if (_) p :: suspended else suspended)
        case None                             =>
          ProcessUtil.frontProcessId.flatMap {
            case Some(p) => suspend(p).map(if (_) p :: suspended else suspended)
            case None    => ZIO.succeed(suspended)
          }
      }
    }

  // Returns whether the process was suspended (it never suspends Command Center itself)
  private def suspend(pid: Long): RIO[Tools, Boolean] =
    (signal(pid, suspend = true) *> playSound("/audio/button-switch-on.wav"))
      .unless(pid == ProcessHandle.current.pid)
      .map(_.isDefined)

  private def resume(pid: Long): RIO[Tools, Unit] =
    signal(pid, suspend = false) *> playSound("/audio/button-switch-off.wav")

  private def signal(pid: Long, suspend: Boolean): Task[Unit] =
    OS.os match {
      case OS.Windows =>
        ZIO.scoped {
          WindowManager
            .openProcess(pid)
            .flatMap(handle =>
              if (suspend) WindowManager.suspendProcess(handle) else WindowManager.resumeProcess(handle)
            )
            .unit
        }

      case _ => PCommand("kill", if (suspend) "-STOP" else "-CONT", pid.toString).successfulExitCode.unit
    }

  private def playSound(resource: String): URIO[Tools, Unit] =
    Tools.playSound(getClass.getResourceAsStream(resource)).forkDaemon.unit
}

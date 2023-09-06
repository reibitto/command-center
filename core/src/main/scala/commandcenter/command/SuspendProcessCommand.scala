package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts.Shortcuts
import commandcenter.util.{OS, ProcessUtil, WindowManager}
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.{Color, Str}
import zio.{RIO, Ref, Scope, Task, ZIO}
import zio.process.Command as PCommand

final case class SuspendProcessCommand(
  commandNames: List[String],
  suspendShortcut: Option[KeyboardShortcut],
  isSuspendedRefs: Ref[Map[Long, Boolean]]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.SuspendProcessCommand
  val title: String = "Suspend/Resume Process"

  val command = decline.Command("suspend", title)(Opts.argument[Long]("pid"))

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsedCommand = command.parse(input.args)
      message <- ZIO
                   .fromEither(parsedCommand)
                   .fold(HelpMessage.formatted, p => Str("PID: ") ++ Color.Magenta(p.toString))
    } yield {
      val run = for {
        pid <- ZIO.fromEither(parsedCommand).mapError(RunError.CliError)
        _ <- for {
               _ <- ZIO.logDebug(s"Toggling suspend for process `$pid`...")
               _ <- OS.os match {
                      case OS.MacOS | OS.Linux =>
                        SuspendProcessCommand.UnixLike.toggleSuspendFrontProcess(Some(pid))

                      case OS.Windows =>
                        ZIO.scoped {
                          SuspendProcessCommand.Windows.toggleSuspendFrontProcess(Some(pid), isSuspendedRefs)
                        }

                      case OS.Other(_) =>
                        ZIO.unit
                    }
             } yield ()
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
      commandNames    <- config.getZIO[Option[List[String]]]("commandNames")
      suspendShortcut <- config.getZIO[Option[KeyboardShortcut]]("suspendShortcut")
      isSuspendedRefs <- Ref.make(Map.empty[Long, Boolean])
      _ <- ZIO
             .foreach(suspendShortcut) { suspendShortcut =>
               Shortcuts.addGlobalShortcut(suspendShortcut)(_ =>
                 (for {
                   _ <- ZIO.logDebug("Toggling suspend for frontmost process...")
                   _ <- OS.os match {
                          case OS.MacOS | OS.Linux =>
                            SuspendProcessCommand.UnixLike.toggleSuspendFrontProcess(None)

                          case OS.Windows =>
                            ZIO.scoped {
                              SuspendProcessCommand.Windows.toggleSuspendFrontProcess(None, isSuspendedRefs)
                            }

                          case OS.Other(_) =>
                            ZIO.unit
                        }
                 } yield ()).ignore
               )
             }
             .mapError(CommandPluginError.UnexpectedException)
    } yield SuspendProcessCommand(commandNames.getOrElse(List("suspend", "pause")), suspendShortcut, isSuspendedRefs)

  object UnixLike {

    def isProcessSuspended(processId: Long): Task[Boolean] = {
      val stateParam = if (OS.os == OS.MacOS) "state=" else "s="

      PCommand("ps", "-o", stateParam, "-p", processId.toString).string.map(_.trim == "T")
    }

    def setProcessState(suspend: Boolean, pid: Long): Task[Unit] = {
      val targetState = if (suspend) "-STOP" else "-CONT"
      PCommand("kill", targetState, pid.toString).exitCode.unit
    }

    def toggleSuspendFrontProcess(pid: Option[Long]): Task[Long] =
      for {
        pid         <- ZIO.fromOption(pid).orElse(ProcessUtil.frontProcessId)
        isSuspended <- isProcessSuspended(pid)
        _           <- setProcessState(!isSuspended, pid)
      } yield pid
  }

  object Windows {

    def toggleSuspendFrontProcess(pid: Option[Long], isSuspendedRefs: Ref[Map[Long, Boolean]]): RIO[Scope, Unit] = {
      for {
        pid          <- ZIO.fromOption(pid).orElse(WindowManager.frontWindow.map(_.pid))
        windowHandle <- WindowManager.openProcess(pid)
        isSuspended  <- isSuspendedRefs.get.map(_.getOrElse(pid, false))
        _ <- if (isSuspended)
               WindowManager
                 .resumeProcess(windowHandle)
                 .tapErrorCause(t => ZIO.logWarningCause("Could not resume process", t))
             else
               WindowManager
                 .suspendProcess(windowHandle)
                 .tapErrorCause(t => ZIO.logWarningCause("Could not resume process", t))
        _ <- isSuspendedRefs.getAndUpdate { m =>
               m + (pid -> !isSuspended)
             }
      } yield ()
    }
  }

}

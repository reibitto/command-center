package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools
import commandcenter.util.{OS, WindowManager}
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.{Color, Str}
import zio.*
import zio.process.Command as PCommand

final case class SuspendProcessCommand(
    commandNames: List[String],
    suspendedPidRef: Ref[Option[Long]]
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
        _   <- SuspendProcessCommand.toggleSuspendProcess(Some(pid), suspendedPidRef)
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
      suspendedPidRef  <- Ref.make(Option.empty[Long])
      _                <- ZIO
             .foreachDiscard(suspendShortcuts) { suspendShortcut =>
               Shortcuts.addGlobalShortcut(suspendShortcut)(_ =>
                 toggleSuspendProcess(None, suspendedPidRef).tapErrorCause { t =>
                   ZIO.logWarningCause("Error running SuspendProcess command", t)
                 }.ignore
               )
             }
             .mapError(CommandPluginError.UnexpectedException.apply)
    } yield SuspendProcessCommand(commandNames, suspendedPidRef)

  def toggleSuspendProcess(pid: Option[Long], suspendedPidRef: Ref[Option[Long]]): RIO[Tools, Unit] =
    OS.os match {
      case OS.MacOS | OS.Linux => UnixLike.toggleSuspendProcess(pid, suspendedPidRef)
      case OS.Windows          => ZIO.scoped(Windows.toggleSuspendProcess(pid, suspendedPidRef))
      case OS.Other(_)         => ZIO.unit
    }

  object UnixLike {

    def suspendProcess(pid: Option[Long], suspendedPidRef: Ref[Option[Long]]): RIO[Tools, Unit] =
      ZIO
        .foreachDiscard(pid) { pid =>
          for {
            _ <- PCommand("kill", "-STOP", pid.toString).exitCode.unit
            _ <- Tools.playSound(getClass.getResourceAsStream("/audio/button-switch-on.wav")).forkDaemon
            _ <- suspendedPidRef.set(Some(pid))
          } yield ()
        }
        .unless(pid.contains(ProcessHandle.current.pid))
        .unit

    def resumeProcess(pid: Option[Long], suspendedPidRef: Ref[Option[Long]]): RIO[Tools, Unit] =
      for {
        pidOpt <- pid.fold(suspendedPidRef.get)(ZIO.some(_))
        _      <- ZIO.foreachDiscard(pidOpt) { pid =>
               for {
                 _ <- PCommand("kill", "-CONT", pid.toString).exitCode.unit
                 _ <- Tools
                        .playSound(getClass.getResourceAsStream("/audio/button-switch-off.wav"))
                        .forkDaemon
                 _ <- suspendedPidRef.set(None)
               } yield ()
             }
      } yield ()

    def toggleSuspendProcess(pid: Option[Long], suspendedPidRef: Ref[Option[Long]]): RIO[Tools, Unit] =
      suspendedPidRef.get.flatMap { suspendedPid =>
        if (suspendedPid.isEmpty) suspendProcess(pid, suspendedPidRef) else resumeProcess(pid, suspendedPidRef)
      }
  }

  object Windows {

    def suspendProcess(pid: Option[Long], suspendedPidRef: Ref[Option[Long]]): RIO[Scope & Tools, Unit] =
      for {
        pidOpt <- pid.fold(WindowManager.frontWindow.map(_.map(_.pid)))(ZIO.some(_))
        tryingToSuspendSelf = pidOpt.contains(ProcessHandle.current.pid)
        _ <- ZIO
               .foreachDiscard(pidOpt) { pid =>
                 for {
                   windowHandle <- WindowManager.openProcess(pid)
                   _            <- WindowManager
                          .suspendProcess(windowHandle)
                          .tapErrorCause(t => ZIO.logWarningCause("Could not suspend process", t))
                   _ <- Tools
                          .playSound(getClass.getResourceAsStream("/audio/button-switch-on.wav"))
                          .forkDaemon
                   _ <- suspendedPidRef.set(Some(pid))
                 } yield ()
               }
               .unless(tryingToSuspendSelf)
      } yield ()

    def resumeProcess(pid: Option[Long], suspendedPidRef: Ref[Option[Long]]): RIO[Scope & Tools, Unit] =
      for {
        pidOpt <- pid.fold(suspendedPidRef.get)(ZIO.some(_))
        _      <- ZIO.foreachDiscard(pidOpt) { pid =>
               for {
                 windowHandle <- WindowManager.openProcess(pid)
                 _            <- WindowManager
                        .resumeProcess(windowHandle)
                        .tapErrorCause(t => ZIO.logWarningCause("Could not resume process", t))
                 _ <- Tools
                        .playSound(getClass.getResourceAsStream("/audio/button-switch-off.wav"))
                        .forkDaemon
                 _ <- suspendedPidRef.set(None)
               } yield ()
             }
      } yield ()

    def toggleSuspendProcess(pid: Option[Long], suspendedPidRef: Ref[Option[Long]]): RIO[Scope & Tools, Unit] =
      suspendedPidRef.get.flatMap { suspendedPid =>
        if (suspendedPid.isEmpty) suspendProcess(pid, suspendedPidRef) else resumeProcess(pid, suspendedPidRef)
      }
  }

}

package commandcenter.ui

import java.io.File

import commandcenter.util.{ AppleScript, OS }
import zio.blocking.Blocking
import zio.process.{ Command => PCommand }
import zio.{ RIO, Task, UIO }

import scala.util.Try

// TODO: Handle Windows and Linux cases. Perhaps fallback to doing nothing since this is only needed for macOS for now.
case class CCProcess(processId: Long, toolsPath: Option[File]) {
  def activate: RIO[Blocking, Unit] =
    toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "activate", processId.toString).exitCode.unit
      case None =>
        AppleScript.runScript(s"""
                                 |tell application "System Events"
                                 | set frontmost of the first process whose unix id is ${ProcessHandle.current.pid} to true
                                 |end tell
                                 |""".stripMargin).unit
    }

  def hide: RIO[Blocking, Unit] =
    toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "hide", processId.toString).exitCode.unit
      // TODO: Fallback to AppleScript if macOS
      case None => UIO.unit
    }
}

object CCProcess {
  def toolsPath: Option[File] =
    sys.env.get("COMMAND_CENTER_TOOLS_PATH").map(new File(_)).orElse {
      Try(System.getProperty("user.home")).toOption
        .map(home => new File(home, ".command-center/cc-tools"))
        .filter(_.exists())
    }

  def get: Task[CCProcess] =
    Task(ProcessHandle.current.pid).map(pid => CCProcess(pid, toolsPath))
}

package commandcenter.tools

import commandcenter.util.AppleScript
import zio.{Task, TaskLayer, ZIO, ZLayer}
import zio.process.Command as PCommand

import java.awt.datatransfer.StringSelection
import java.awt.Toolkit
import java.io.File
import scala.util.Try

// TODO: Handle Windows and Linux cases. Perhaps fallback to doing nothing since this is only needed for macOS for now.
final case class ToolsLive(pid: Long, toolsPath: Option[File]) extends Tools {
  def processId: Long = pid

  def activate: Task[Unit] =
    toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "activate", pid.toString).exitCode.unit
      case None =>
        AppleScript
          .runScript(s"""
                        |tell application "System Events"
                        | set frontmost of the first process whose unix id is $processId to true
                        |end tell
                        |""".stripMargin)
          .unit
    }

  def hide: Task[Unit] =
    toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "hide", pid.toString).exitCode.unit
      // TODO: Fallback to AppleScript if macOS
      case None => ZIO.unit
    }

  def setClipboard(text: String): Task[Unit] =
    toolsPath match {
      case Some(ccTools) =>
        PCommand(ccTools.getAbsolutePath, "set-clipboard", text).exitCode.unit
      case None =>
        ZIO.attemptBlocking {
          Toolkit.getDefaultToolkit.getSystemClipboard.setContents(new StringSelection(text), null)
        }
    }

  def beep: Task[Unit] = ZIO.attempt(Toolkit.getDefaultToolkit.beep())
}

object ToolsLive {

  def make: TaskLayer[Tools] = {
    ZLayer {
      (for {
        pid <- ZIO.attempt(ProcessHandle.current.pid)
        toolsPath = sys.env.get("COMMAND_CENTER_TOOLS_PATH").map(new File(_)).orElse {
                      Try(System.getProperty("user.home")).toOption
                        .map(home => new File(home, ".command-center/cc-tools"))
                        .filter(_.exists())
                    }
      } yield new ToolsLive(pid, toolsPath))
    }
  }
}

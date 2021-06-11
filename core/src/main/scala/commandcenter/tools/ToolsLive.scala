package commandcenter.tools

import commandcenter.util.AppleScript
import zio.blocking.Blocking
import zio.process.{ Command => PCommand }
import zio.{ Has, Task, TaskLayer, UIO }

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import scala.util.Try

// TODO: Handle Windows and Linux cases. Perhaps fallback to doing nothing since this is only needed for macOS for now.
final case class ToolsLive(pid: Long, toolsPath: Option[File], blocking: Blocking.Service) extends Tools {
  def processId: Long = pid

  def activate: Task[Unit] =
    (toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "activate", pid.toString).exitCode.unit
      case None          =>
        AppleScript
          .runScript(s"""
                        |tell application "System Events"
                        | set frontmost of the first process whose unix id is $processId to true
                        |end tell
                        |""".stripMargin)
          .unit
    }).provide(Has(blocking))

  def hide: Task[Unit] =
    toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "hide", pid.toString).exitCode.unit.provide(Has(blocking))
      // TODO: Fallback to AppleScript if macOS
      case None          => UIO.unit
    }

  def setClipboard(text: String): Task[Unit] =
    toolsPath match {
      case Some(ccTools) =>
        PCommand(ccTools.getAbsolutePath, "set-clipboard", text).exitCode.unit.provide(Has(blocking))
      case None          =>
        blocking.effectBlocking {
          Toolkit.getDefaultToolkit.getSystemClipboard.setContents(new StringSelection(text), null)
        }
    }
}

object ToolsLive {
  def make: TaskLayer[Has[Tools]] =
    (for {
      pid      <- Task(ProcessHandle.current.pid).toManaged_
      toolsPath = sys.env.get("COMMAND_CENTER_TOOLS_PATH").map(new File(_)).orElse {
                    Try(System.getProperty("user.home")).toOption
                      .map(home => new File(home, ".command-center/cc-tools"))
                      .filter(_.exists())
                  }
    } yield new ToolsLive(pid, toolsPath, Blocking.Service.live)).toLayer
}

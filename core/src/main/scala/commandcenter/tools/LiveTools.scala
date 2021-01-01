package commandcenter.tools

import commandcenter.util.AppleScript
import zio.blocking.{ effectBlocking, Blocking }
import zio.process.{ Command => PCommand }
import zio.{ RIO, UIO }

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

// TODO: Handle Windows and Linux cases. Perhaps fallback to doing nothing since this is only needed for macOS for now.
class LiveTools(pid: Long, toolsPath: Option[File]) extends Tools.Service {
  def processId: UIO[Long] = UIO(pid)

  def activate: RIO[Blocking, Unit] =
    toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "activate", pid.toString).exitCode.unit
      case None          =>
        AppleScript
          .runScript(s"""
                        |tell application "System Events"
                        | set frontmost of the first process whose unix id is $processId to true
                        |end tell
                        |""".stripMargin)
          .unit
    }

  def hide: RIO[Blocking, Unit] =
    toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "hide", pid.toString).exitCode.unit
      // TODO: Fallback to AppleScript if macOS
      case None          => UIO.unit
    }

  def setClipboard(text: String): RIO[Blocking, Unit] =
    toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "set-clipboard", text).exitCode.unit
      case None          =>
        effectBlocking {
          Toolkit.getDefaultToolkit.getSystemClipboard.setContents(new StringSelection(text), null)
        }
    }
}

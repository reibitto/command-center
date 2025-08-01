package commandcenter.tools

import commandcenter.util.AppleScript
import zio.*
import zio.process.Command as PCommand

import java.awt.datatransfer.{DataFlavor, StringSelection}
import java.awt.Toolkit
import java.io.{BufferedInputStream, File, InputStream}
import javax.sound.sampled.{AudioSystem, FloatControl, LineEvent}
import scala.util.Try

// TODO: Handle Windows and Linux cases. Perhaps fallback to doing nothing since this is only needed for macOS for now.
final case class ToolsLive(pid: Long, toolsPath: Option[File]) extends Tools {
  def processId: Long = pid

  def activate: Task[Unit] =
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

  def hide: Task[Unit] =
    toolsPath match {
      case Some(ccTools) => PCommand(ccTools.getAbsolutePath, "hide", pid.toString).exitCode.unit
      // TODO: Fallback to AppleScript if macOS
      case None => ZIO.unit
    }

  def getClipboard: Task[String] =
    ZIO.attemptBlocking {
      // Not using `isDataFlavorAvailable` here because technically it's a race condition unless we resort to locking.
      // Plus it doesn't prevent stuff like:
      //   Exception "java.lang.ClassNotFoundException: com/intellij/codeInsight/editorActions/FoldingData" while
      //   constructing DataFlavor for: application/x-java-jvm-local-objectref;
      //   class=com.intellij.codeInsight.editorActions.FoldingData
      Try {
        Toolkit.getDefaultToolkit.getSystemClipboard.getData(DataFlavor.stringFlavor).asInstanceOf[String]
      }.getOrElse("")
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

  def playSound(inputStream: InputStream): Task[Unit] =
    ZIO.scoped {
      for {
        audioInputStream <- ZIO.attemptBlocking(AudioSystem.getAudioInputStream(new BufferedInputStream(inputStream)))
        _                <- ZIO.addFinalizer(ZIO.succeed(audioInputStream.close()))
        clip             <- ZIO.attemptBlocking(AudioSystem.getClip)
        _                <- ZIO.addFinalizer(ZIO.succeed(clip.close()))
        donePromise      <- Promise.make[Nothing, Unit]
        _                <- ZIO
               .async[Any, Throwable, Boolean] { cb =>
                 clip.addLineListener { e =>
                   if (e.getType == LineEvent.Type.STOP) {
                     cb(donePromise.succeed(()))
                   }
                 }
               }
               .fork
        _ <- ZIO.attemptBlocking {
               clip.open(audioInputStream)

               val control = clip.getControl(FloatControl.Type.MASTER_GAIN).asInstanceOf[FloatControl]
               control.setValue(-15) // unit is decibels

               clip.start()
             }
        _ <- donePromise.await
      } yield ()
    }
}

object ToolsLive {

  def make: TaskLayer[Tools] =
    ZLayer {
      for {
        pid <- ZIO.attempt(ProcessHandle.current.pid)
        toolsPath = sys.env.get("COMMAND_CENTER_TOOLS_PATH").map(new File(_)).orElse {
                      Try(java.lang.System.getProperty("user.home")).toOption
                        .map(home => new File(home, ".command-center/cc-tools"))
                        .filter(_.exists())
                    }
      } yield new ToolsLive(pid, toolsPath)
    }
}

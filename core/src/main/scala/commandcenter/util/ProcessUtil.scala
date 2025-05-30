package commandcenter.util

import zio.*
import zio.process.Command as PCommand

import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Path
import scala.util.Try

object ProcessUtil {

  def openBrowser(url: String, firefoxPath: Option[Path] = None): Task[Unit] = {
    val scheme = Try(new URI(url)).toOption.map(_.getScheme)

    scheme match {
      case Some("moz-extension") =>
        ZIO.foreachDiscard(firefoxPath) { path =>
          PCommand(path.toString, url).exitCode.unit
        }

      case _ =>
        OS.os match {
          case OS.MacOS =>
            PCommand("open", url).exitCode.unit

          case OS.Linux =>
            PCommand("xdg-open", url).exitCode.unit

          case OS.Windows | OS.Other(_) =>
            ZIO.attemptBlocking(
              Desktop.getDesktop.browse(new URI(url))
            )
        }
    }
  }

  def frontProcessId: Task[Option[Long]] =
    OS.os match {
      case OS.MacOS =>
        for {
          asn       <- PCommand("lsappinfo", "front").string
          pidString <- PCommand("lsappinfo", "info", "-only", "pid", asn.trim).string
          pid       <- pidString.split('=') match {
                   case Array(_, pid) => ZIO.succeed(pid.trim.toLong)
                   case _             => ZIO.fail(new Exception(s"pid could not be extracted from: $pidString"))
                 }
        } yield Some(pid)

      case OS.Windows =>
        WindowManager.frontWindow.map(_.map(_.pid))

      case _ =>
        ZIO.fail(
          new UnsupportedOperationException(s"Getting the frontmost process's PID not supported yet for ${OS.os}")
        )
    }

  def browseToFile(file: File): Task[Unit] =
    OS.os match {
      case OS.MacOS =>
        val command =
          if (file.isDirectory) PCommand("open", file.getAbsolutePath)
          else PCommand("open", "-R", file.getAbsolutePath)

        command.successfulExitCode.unit

      case OS.Windows =>
        val arg =
          if (file.isDirectory) file.getCanonicalPath
          else s"/select,${file.getAbsolutePath}"

        PCommand("explorer.exe", arg).successfulExitCode.unit

      // TODO: To properly support Linux, we probably need to detect the Linux flavor
      case OS.Linux | OS.Other(_) => ZIO.attemptBlocking(Desktop.getDesktop.browseFileDirectory(file))
    }

}

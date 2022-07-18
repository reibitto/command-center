package commandcenter.util

import zio.{RIO, UIO, ZIO}
import zio.process.Command as PCommand
import zio.ZIO.attemptBlocking

import java.awt.Desktop
import java.io.File
import java.net.URI

object ProcessUtil {

  def openBrowser(url: String): RIO[Any, Unit] =
    OS.os match {
      case OS.MacOS =>
        PCommand("open", url).exitCode.unit

      case OS.Linux =>
        PCommand("xdg-open", url).exitCode.unit

      case OS.Windows | OS.Other(_) =>
        attemptBlocking(
          Desktop.getDesktop.browse(new URI(url))
        )
    }

  def frontProcessId: RIO[Any, Long] =
    OS.os match {
      case OS.MacOS =>
        for {
          asn       <- PCommand("lsappinfo", "front").string
          pidString <- PCommand("lsappinfo", "info", "-only", "pid", asn.trim).string
          pid <- pidString.split('=') match {
                   case Array(_, pid) => ZIO.succeed(pid.trim.toLong)
                   case _             => ZIO.fail(new Exception(s"pid could not be extracted from: $pidString"))
                 }
        } yield pid

      case _ =>
        ZIO.fail(
          new UnsupportedOperationException(s"Getting the frontmost process's PID not supported yet for ${OS.os}")
        )
    }

  def browseFile(file: File): RIO[Any, Unit] =
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
      case OS.Linux | OS.Other(_) => attemptBlocking(Desktop.getDesktop.browseFileDirectory(file))
    }

}

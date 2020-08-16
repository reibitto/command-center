package commandcenter.util

import java.awt.Desktop
import java.net.URI

import zio.blocking.{ Blocking, _ }
import zio.process.{ Command => PCommand }
import zio.{ RIO, UIO, ZIO }

object ProcessUtil {
  def openBrowser(url: String): RIO[Blocking, Unit] =
    OS.os match {
      case OS.MacOS              =>
        PCommand("open", url).exitCode.unit

      case OS.Linux              =>
        PCommand("xdg-open", url).exitCode.unit

      case OS.Windows | OS.Other =>
        effectBlocking(
          Desktop.getDesktop.browse(new URI(url))
        )
    }

  def frontProcessId: RIO[Blocking, Long] =
    OS.os match {
      case OS.MacOS =>
        for {
          asn       <- PCommand("lsappinfo", "front").string
          pidString <- PCommand("lsappinfo", "info", "-only", "pid", asn.trim).string
          pid       <- pidString.split('=') match {
                         case Array(_, pid) => UIO(pid.trim.toLong)
                         case _             => ZIO.fail(new Exception(s"pid could not be extracted from: $pidString"))
                       }
        } yield pid

      case _        =>
        ZIO.fail(
          new UnsupportedOperationException(s"Getting the frontmost process's PID not supported yet for ${OS.os}")
        )
    }

}

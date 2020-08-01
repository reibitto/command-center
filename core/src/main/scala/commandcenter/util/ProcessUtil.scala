package commandcenter.util

import java.awt.Desktop
import java.net.URI

import zio.RIO
import zio.blocking.{ Blocking, _ }
import zio.process.{ Command => PCommand }

object ProcessUtil {
  def openBrowser(url: String): RIO[Blocking, Unit] =
    OS.os match {
      case OS.MacOS   =>
        PCommand("open", url).exitCode.unit

      case OS.Linux   =>
        PCommand("xdg-open", url).exitCode.unit

      case OS.Windows =>
        PCommand("start", url).exitCode.unit

      case OS.Other   =>
        effectBlocking(
          Desktop.getDesktop.browse(new URI(url))
        )
    }
}

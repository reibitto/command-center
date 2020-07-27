package commandcenter.util

import java.awt.{ Desktop, Toolkit }
import java.awt.datatransfer.StringSelection
import java.net.URI

import zio.RIO
import zio.blocking.{ Blocking, _ }

object ProcessUtil {
  def copyToClipboard(s: String): RIO[Blocking, Unit] =
    effectBlocking {
      Toolkit.getDefaultToolkit.getSystemClipboard.setContents(new StringSelection(s), null)
    }

  def openBrowser(url: String): RIO[Blocking, Unit] =
    effectBlocking(
      Desktop.getDesktop.browse(new URI(url))
    ) // TODO: Make browser configurable and use Command to open url instead
}

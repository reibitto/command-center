package commandcenter.daemon.shortcuts

import java.awt.Toolkit

import com.tulskiy.keymaster.common.{ HotKey, HotKeyListener, Provider }
import commandcenter.CCRuntime.Env
import commandcenter.daemon.Main.unsafeRunAsync_
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts.Shortcuts
import javax.swing.KeyStroke
import zio._

import scala.util.Try

class LiveShortcuts(val provider: Provider) extends Shortcuts.Service {
  def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): Task[Unit] =
    UIO {
      provider.register(
        KeyStroke.getKeyStroke(shortcut.shortcut),
        new HotKeyListener {
          def onHotKey(hotKey: HotKey): Unit = unsafeRunAsync_(handler(shortcut))
        }
      )
    }
}

object LiveShortcuts {
  def layer: TaskLayer[Shortcuts] =
    ZLayer.fromManaged {
      ZManaged.make(Task {
        // This is a hack for macOS to get the separate `java` Application started (it shows up as an icon in the Dock).
        // Otherwise the hot key provider won't be fully started up yet.
        Try(Toolkit.getDefaultToolkit)

        new LiveShortcuts(Provider.getCurrentProvider(true))
      }) { shortcuts =>
        UIO {
          shortcuts.provider.reset()
          shortcuts.provider.stop()
        }
      }
    }
}

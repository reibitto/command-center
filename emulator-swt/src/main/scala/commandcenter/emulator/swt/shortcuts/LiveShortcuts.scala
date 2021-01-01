package commandcenter.emulator.swt.shortcuts

import java.awt.Toolkit
import scala.util.Try

import com.tulskiy.keymaster.common.{ HotKey, HotKeyListener, Provider }
import commandcenter.CCRuntime.Env
import commandcenter.emulator.swt.event.KeyboardShortcutUtil
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts.Shortcuts
import zio._

class LiveShortcuts(val provider: Provider, runtime: Runtime[Env]) extends Shortcuts.Service {
  def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): Task[Unit] =
    UIO {
      provider.register(
        KeyboardShortcutUtil.toKeyStroke(shortcut),
        new HotKeyListener {
          def onHotKey(hotKey: HotKey): Unit =
            runtime.unsafeRunAsync_(handler(shortcut))
        }
      )
    }
}

object LiveShortcuts {
  def layer(runtime: Runtime[Env]): TaskLayer[Shortcuts] =
    ZLayer.fromManaged {
      ZManaged.make(Task {
        // This is a hack for macOS to get the separate `java` Application started (it shows up as an icon in the Dock).
        // Otherwise the hot key provider won't be fully started up yet.
        Try(Toolkit.getDefaultToolkit)

        new LiveShortcuts(Provider.getCurrentProvider(true), runtime)
      }) { shortcuts =>
        UIO {
          shortcuts.provider.reset()
          shortcuts.provider.stop()
        }
      }
    }
}

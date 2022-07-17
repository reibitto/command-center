package commandcenter.emulator.swing.shortcuts

import com.tulskiy.keymaster.common.{HotKey, HotKeyListener, Provider}
import commandcenter.emulator.swing.event.KeyboardShortcutUtil
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts.Shortcuts
import commandcenter.CCRuntime.Env
import zio.*

import java.awt.Toolkit
import scala.util.Try
import zio.managed._

final case class ShortcutsLive(provider: Provider, runtime: Runtime[Env]) extends Shortcuts {

  def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): Task[Unit] =
    ZIO.succeed {
      provider.register(
        KeyboardShortcutUtil.toKeyStroke(shortcut),
        new HotKeyListener {
          def onHotKey(hotKey: HotKey): Unit =
            runtime.unsafeRunAsync(handler(shortcut))
        }
      )
    }
}

object ShortcutsLive {

  def layer(runtime: Runtime[Env]): TaskLayer[Shortcuts] =
    ZLayer.fromManaged {
      ZManaged.acquireReleaseWith(ZIO.attempt {
        // This is a hack for macOS to get the separate `java` Application started (it shows up as an icon in the Dock).
        // Otherwise the hot key provider won't be fully started up yet.
        Try(Toolkit.getDefaultToolkit)

        new ShortcutsLive(Provider.getCurrentProvider(true), runtime)
      }) { shortcuts =>
        ZIO.succeed {
          shortcuts.provider.reset()
          shortcuts.provider.stop()
        }
      }
    }
}

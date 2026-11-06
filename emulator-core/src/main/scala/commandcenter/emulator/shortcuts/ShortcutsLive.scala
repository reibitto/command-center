package commandcenter.emulator.shortcuts

import com.tulskiy.keymaster.common.{HotKey, HotKeyListener, Provider}
import commandcenter.event.{KeyModifier, KeyboardShortcut}
import commandcenter.shortcuts.Shortcuts
import commandcenter.util.OS
import commandcenter.util.RuntimeExtensions.*
import commandcenter.CCRuntime.Env
import zio.*

import java.awt.event.InputEvent
import java.awt.Toolkit
import javax.swing.KeyStroke
import scala.util.Try

final case class ShortcutsLive(provider: Provider) extends Shortcuts {

  def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): RIO[Env, Unit] =
    for {
      runtime <- ZIO.runtime[Env]
      shortcutKeyStroke = ShortcutsLive.toKeyStroke(shortcut)
      // Unregister any previous shortcuts so that if a Command is reloaded, it can register its own shortcut again.
      _ <- ZIO.attempt(provider.unregister(shortcutKeyStroke)).ignore
      _ <- ZIO.succeed {
             provider.register(
               shortcutKeyStroke,
               new HotKeyListener {
                 def onHotKey(hotKey: HotKey): Unit = runtime.unsafeFork(handler(shortcut))
               }
             )
           }
      _ <- ZIO.logDebug(s"Registered global shortcut: $shortcut")
    } yield ()
}

object ShortcutsLive {

  def layer(useSwingEventQueue: Boolean): TaskLayer[Shortcuts] =
    ZLayer.scoped {
      ZIO.acquireRelease(ZIO.attempt {
        // This is a hack for macOS to get the separate `java` Application started (it shows up as an icon in the Dock).
        // Otherwise the hot key provider won't be fully started up yet. Elsewhere it would needlessly start AWT.
        if (useSwingEventQueue || OS.os == OS.MacOS) Try(Toolkit.getDefaultToolkit)

        ShortcutsLive(Provider.getCurrentProvider(useSwingEventQueue))
      })(shortcuts =>
        ZIO.succeed {
          shortcuts.provider.reset()
          shortcuts.provider.stop()
        }
      )
    }

  def toKeyStroke(shortcut: KeyboardShortcut): KeyStroke =
    KeyStroke.getKeyStroke(
      shortcut.key.value,
      shortcut.modifiers.foldLeft(0) {
        case (mask, KeyModifier.Shift)    => mask | InputEvent.SHIFT_DOWN_MASK
        case (mask, KeyModifier.Control)  => mask | InputEvent.CTRL_DOWN_MASK
        case (mask, KeyModifier.Alt)      => mask | InputEvent.ALT_DOWN_MASK
        case (mask, KeyModifier.AltGraph) => mask | InputEvent.ALT_GRAPH_DOWN_MASK
        case (mask, KeyModifier.Meta)     => mask | InputEvent.META_DOWN_MASK
      }
    )
}

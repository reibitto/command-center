package commandcenter.emulator.swing.shortcuts

import com.tulskiy.keymaster.common.{HotKey, HotKeyListener, Provider}
import commandcenter.emulator.swing.event.KeyboardShortcutUtil
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts.Shortcuts
import commandcenter.CCRuntime.Env
import zio.*

import java.awt.Toolkit
import scala.util.Try

final case class ShortcutsLive(provider: Provider) extends Shortcuts {

  def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): RIO[Env, Unit] =
    for {
      runtime <- ZIO.runtime[Env]
      _ <- ZIO.succeed {
             provider.register(
               KeyboardShortcutUtil.toKeyStroke(shortcut),
               new HotKeyListener {
                 def onHotKey(hotKey: HotKey): Unit = {
                   Unsafe.unsafe { implicit u =>
                     runtime.unsafe.fork(handler(shortcut))
                   }
                 }
               }
             )
           }
    } yield ()
}

object ShortcutsLive {

  def layer: ZLayer[Scope, Throwable, Shortcuts] = {
    ZLayer {
      for {
        shortcuts <- ZIO.acquireRelease(ZIO.attempt {
                       // This is a hack for macOS to get the separate `java` Application started (it shows up as an icon in the Dock).
                       // Otherwise the hot key provider won't be fully started up yet.
                       Try(Toolkit.getDefaultToolkit)

                       new ShortcutsLive(Provider.getCurrentProvider(true))
                     })({ shortcuts =>
                       ZIO.succeed {
                         shortcuts.provider.reset()
                         shortcuts.provider.stop()
                       }
                     })
      } yield shortcuts
    }
  }
}

package commandcenter.daemon

import java.awt.Toolkit

import com.tulskiy.keymaster.common.{ HotKey, HotKeyListener, Provider }
import commandcenter.CCRuntime.Env
import commandcenter.daemon.Main.unsafeRunAsync_
import javax.swing.KeyStroke
import zio.{ Task, UIO, ZIO, ZManaged }

import scala.util.Try

final case class HotKeyProvider(provider: Provider) {
  def registerHotKey(keyStroke: KeyStroke)(
    handler: HotKey => ZIO[Env, Nothing, Unit]
  ): UIO[Unit] =
    UIO {
      provider.register(
        keyStroke,
        new HotKeyListener {
          def onHotKey(hotKey: HotKey): Unit = unsafeRunAsync_(handler(hotKey))
        }
      )
    }
}

object HotKeyProvider {
  def make: ZManaged[Any, Throwable, HotKeyProvider] =
    ZManaged.make(Task {
      // This is a hack for macOS to get the separate `java` Application started (it shows up as an icon in the Dock).
      // Otherwise the hot key provider won't be fully started up yet.
      Try(Toolkit.getDefaultToolkit)

      HotKeyProvider(Provider.getCurrentProvider(true))
    }) { provider =>
      UIO {
        provider.provider.reset()
        provider.provider.stop()
      }
    }
}

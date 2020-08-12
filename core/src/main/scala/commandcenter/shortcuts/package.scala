package commandcenter

import commandcenter.CCRuntime.Env
import commandcenter.event.KeyboardShortcut
import zio._

package object shortcuts {
  type Shortcuts = Has[Shortcuts.Service]

  object Shortcuts {
    trait Service {
      def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): Task[Unit]
    }

    def unsupported: ULayer[Shortcuts] =
      ZLayer.succeed(
        new Shortcuts.Service {
          def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): Task[Unit] =
            Task.unit
        }
      )
  }

  def addGlobalShortcut(
    shortcut: KeyboardShortcut
  )(handler: KeyboardShortcut => URIO[Env, Unit]): RIO[Shortcuts, Unit] =
    ZIO.accessM[Shortcuts](_.get.addGlobalShortcut(shortcut)(handler))
}

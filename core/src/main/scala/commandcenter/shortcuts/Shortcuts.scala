package commandcenter.shortcuts

import commandcenter.CCRuntime.Env
import commandcenter.event.KeyboardShortcut
import zio.{ Has, RIO, Task, ULayer, URIO, ZIO, ZLayer }

trait Shortcuts {
  def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): Task[Unit]
}

object Shortcuts {
  def addGlobalShortcut(
    shortcut: KeyboardShortcut
  )(handler: KeyboardShortcut => URIO[Env, Unit]): RIO[Has[Shortcuts], Unit] =
    ZIO.serviceWith[Shortcuts](_.addGlobalShortcut(shortcut)(handler))

  def unsupported: ULayer[Has[Shortcuts]] =
    ZLayer.succeed(
      new Shortcuts {
        def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): Task[Unit] =
          Task.unit
      }
    )
}

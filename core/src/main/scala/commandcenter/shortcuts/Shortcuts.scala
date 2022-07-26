package commandcenter.shortcuts

import commandcenter.event.KeyboardShortcut
import commandcenter.CCRuntime.Env
import zio.{RIO, Task, ULayer, URIO, ZIO, ZLayer}

trait Shortcuts {
  def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): RIO[Env, Unit]
}

object Shortcuts {

  def addGlobalShortcut(
    shortcut: KeyboardShortcut
  )(handler: KeyboardShortcut => URIO[Env, Unit]): RIO[Env, Unit] =
    ZIO.serviceWithZIO[Shortcuts](_.addGlobalShortcut(shortcut)(handler))

  def unsupported: ULayer[Shortcuts] =
    ZLayer.succeed(
      new Shortcuts {

        def addGlobalShortcut(shortcut: KeyboardShortcut)(handler: KeyboardShortcut => URIO[Env, Unit]): Task[Unit] =
          ZIO.unit
      }
    )
}

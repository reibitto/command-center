package commandcenter

import commandcenter.event.KeyboardShortcut
import commandcenter.CCRuntime.Env
import zio.*

final case class ConfigFake() extends Conf {

  def config: UIO[CCConfig] = ZIO.succeed(
    CCConfig(
      commands = Vector.empty,
      aliases = Map.empty,
      general = GeneralConfig(150.millis),
      display = DisplayConfig(width = 0, maxHeight = 0, opacity = 1.0f, fonts = Nil),
      keyboard = KeyboardConfig(KeyboardShortcut.empty, None),
      globalActions = Vector.empty
    )
  )

  def load: Task[CCConfig] = config

  def reload: RIO[Env, CCConfig] = config
}

object ConfigFake {
  def layer: ULayer[Conf] = ZLayer.succeed(ConfigFake())
}

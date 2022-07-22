package commandcenter

import commandcenter.event.KeyboardShortcut
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

  override def reload: Task[CCConfig] = config
}

object ConfigFake {
  def layer: ULayer[Conf] = ZLayer.succeed(ConfigFake())
}

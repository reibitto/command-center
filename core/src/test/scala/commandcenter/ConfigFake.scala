package commandcenter

import commandcenter.CCRuntime.PartialEnv
import commandcenter.event.KeyboardShortcut
import zio.duration._
import zio.{ Has, RIO, UIO, ULayer, ZLayer }

final case class ConfigFake() extends Conf {
  def config: UIO[CCConfig]                      = UIO(
    CCConfig(
      commands = Vector.empty,
      aliases = Map.empty,
      general = GeneralConfig(150.millis),
      display = DisplayConfig(width = 0, maxHeight = 0, opacity = 1.0f, fonts = Nil),
      keyboard = KeyboardConfig(KeyboardShortcut.empty, None),
      globalActions = Vector.empty
    )
  )

  override def reload: RIO[PartialEnv, CCConfig] = config
}

object ConfigFake {
  def layer: ULayer[Has[Conf]] = ZLayer.succeed(ConfigFake())
}

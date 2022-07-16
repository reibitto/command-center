package commandcenter

import commandcenter.event.KeyboardShortcut
import commandcenter.CCRuntime.PartialEnv
import zio.{Has, RIO, UIO, ULayer, ZLayer}
import zio.duration.*

final case class ConfigFake() extends Conf {

  def config: UIO[CCConfig] = UIO(
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

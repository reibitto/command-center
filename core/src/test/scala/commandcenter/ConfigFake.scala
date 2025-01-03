package commandcenter

import commandcenter.event.KeyboardShortcut
import commandcenter.CCRuntime.Env
import zio.*

final case class ConfigFake() extends Conf {

  def config: UIO[CCConfig] = ZIO.succeed(
    CCConfig(
      commands = Vector.empty,
      aliases = Map.empty,
      general = GeneralConfig(
        debounceDelay = 150.millis,
        opTimeout = None,
        reopenDelay = None,
        hideOnKeyRelease = false,
        keepOpen = false
      ),
      display = DisplayConfig(width = 0, maxHeight = 0, opacity = 1.0f, fonts = Nil, alternateOpacity = None),
      keyboard = KeyboardConfig(KeyboardShortcut.empty, None, KeyboardShortcut.empty),
      globalActions = Vector.empty
    )
  )

  def load: Task[CCConfig] = config

  def reload: RIO[Env, CCConfig] = config
}

object ConfigFake {
  def layer: ULayer[Conf] = ZLayer.succeed(ConfigFake())
}

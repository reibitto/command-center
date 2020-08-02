package commandcenter.util

import java.awt.{ GraphicsDevice, GraphicsEnvironment }

import zio.{ Task, UIO }

object GraphicsUtil {
  def isOpacitySupported: UIO[Boolean] =
    Task(
      GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice
        .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)
    ).orElseSucceed(false)
}

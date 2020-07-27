package commandcenter.util

import zio.RIO
import zio.blocking._
import zio.process.Command

object TTS {
  def say(text: String): RIO[Blocking, Unit] =
    Command("say", text).exitCode.unit
}

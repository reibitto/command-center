package commandcenter.util

import zio.process.Command
import zio.RIO

object TTS {

  // TODO: Support other OSes
  def say(text: String): RIO[Any, Unit] =
    Command("say", text).exitCode.unit
}

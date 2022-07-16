package commandcenter.util

import zio.blocking.*
import zio.process.Command
import zio.RIO

object TTS {

  // TODO: Support other OSes
  def say(text: String): RIO[Blocking, Unit] =
    Command("say", text).exitCode.unit
}

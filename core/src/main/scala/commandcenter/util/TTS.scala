package commandcenter.util

import zio.*
import zio.process.Command

object TTS {

  // TODO: Support other OSes
  def say(text: String): Task[Unit] =
    Command("say", text).exitCode.unit
}

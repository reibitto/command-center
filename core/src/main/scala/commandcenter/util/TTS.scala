package commandcenter.util

import zio.process.Command
import zio.Task

object TTS {

  // TODO: Support other OSes
  def say(text: String): Task[Unit] =
    Command("say", text).exitCode.unit
}

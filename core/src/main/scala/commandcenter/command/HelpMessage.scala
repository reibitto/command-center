package commandcenter.command

import com.monovore.decline.Help
import fansi.Str

object HelpMessage {
  def formatted(help: Help): Str =
    fansi.Color.Red(help.errors.mkString("\n")) ++ "\nUsage: " ++ fansi.Color.Yellow(help.usage.mkString("\n"))
}

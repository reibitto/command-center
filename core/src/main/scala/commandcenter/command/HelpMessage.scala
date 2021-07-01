package commandcenter.command

import com.monovore.decline.Help
import fansi.Str

object HelpMessage {
  def formatted(help: Help): Str = {
    val errors = fansi.Color.Red(help.errors.mkString("\n"))

    if (help.usage.exists(_.nonEmpty))
      errors ++ "\nUsage: " ++ fansi.Color.Yellow(help.usage.mkString("\n"))
    else
      errors

  }
}

package commandcenter.command

import com.monovore.decline.Help
import fansi.{Color, Str}

object HelpMessage {

  def formatted(help: Help): Str = {
    val errors = Color.Red(help.errors.mkString("\n"))

    if (help.usage.exists(_.nonEmpty))
      errors ++ "\nUsage: " ++ Color.Yellow(help.usage.mkString("\n"))
    else
      errors

  }
}

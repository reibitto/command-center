import sbt.Keys._

import scala.Console

object ConsoleHelper {
  def prompt: String               = s"${Console.CYAN}>${Console.RESET} "
  def header(text: String): String = s"${Console.GREEN}$text${Console.RESET}"

  def item(text: String): String =
    s"${Console.RED}> ${Console.CYAN}$text${Console.RESET}"

  def welcomeMessage =
    onLoadMessage :=
      s"""
         |${header(",---.                           .   ,---.         .         ")}          
         |${header("|     ,-. ,-,-. ,-,-. ,-. ,-. ,-|   |     ,-. ,-. |- ,-. ,-.")} 
         |${header("|     | | | | | | | | ,-| | | | |   |     |-' | | |  |-' |  ")}   
         |${header(s"`---' `-' ' ' ' ' ' ' `-^ ' ' `-'   `---' `-' ' ' `' `-' '   ${version.value}")}
         |Useful sbt tasks:
         |${item("~compile")} - Compile all modules with file-watch enabled
         |${item("fmt")} - Run scalafmt on the entire project
         |${item("cli-client/run")} - Run Command Center CLI client (interactive mode by default)
         |${item("daemon/run")} - Run Command Center in daemon mode (cmd+space to summon terminal emulator)
         |${item("cli-client/assembly")} - Create an executable JAR for running command line utility
         |${item("cli-client/graalvm-native-image:packageBin")} - Create a native executable of the CLI client
         |${item("daemon/assembly")} - Create an executable JAR for running in daemon mode
         |${item("daemon/graalvm-native-image:packageBin")} - Create a native executable of the daemon ${Console.RED}(work in progress)
      """.stripMargin
}

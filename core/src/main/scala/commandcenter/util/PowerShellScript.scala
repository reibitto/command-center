package commandcenter.util

import commandcenter.cache.ZCache
import zio.*
import zio.process.*

object PowerShellScript {

  // Escapes a value for use inside a single-quoted PowerShell string
  def escape(s: String): String = s.replace("'", "''")

  def loadFunction2[A, A2](cache: ZCache[String, String])(resource: String): (A, A2) => Task[String] =
    (a, a2) =>
      cache(s"powershell/$resource").flatMap { script =>
        Command("powershell", ScriptTemplate.fill(script, Seq(a, a2), escape)).string
      }

  def executeCommand(command: String): IO[CommandError, Process] =
    Command("powershell", "-command", command).run
}

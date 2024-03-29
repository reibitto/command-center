package commandcenter.util

import zio.*
import zio.cache.Cache
import zio.process.*

object PowerShellScript {

  def loadFunction2[A, A2](
      cache: Cache[String, Nothing, String]
  )(resource: String): (A, A2) => Task[String] =
    (a, a2) =>
      for {
        script <- cache.get(resource)
        result <- Command("powershell", script.replace("{0}", a.toString).replace("{1}", a2.toString)).string
      } yield result

  def executeCommand(command: String): IO[CommandError, Process] =
    Command("powershell", "-command", command).run
}

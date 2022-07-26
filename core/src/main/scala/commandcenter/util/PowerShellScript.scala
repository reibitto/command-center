package commandcenter.util

import zio.cache.Cache
import zio.process.Command
import zio.Task

object PowerShellScript {

  def loadFunction2[A, A2](
    cache: Cache[String, Nothing, String]
  )(resource: String): (A, A2) => Task[String] =
    (a, a2) =>
      for {
        script <- cache.get(resource)
        result <- Command("powershell", script.replace("{0}", a.toString).replace("{1}", a2.toString)).string
      } yield result
}

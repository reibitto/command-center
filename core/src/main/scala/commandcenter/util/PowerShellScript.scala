package commandcenter.util

import commandcenter.command.cache.InMemoryCache
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.Duration
import zio.process.Command
import zio.{ RIO, UIO, ZManaged }

import scala.io.Source

object PowerShellScript {
  def loadFunction2[A, A2](resource: String): (A, A2) => RIO[Blocking with InMemoryCache with Clock, String] =
    (a, a2) => {
      for {
        script <- InMemoryCache.getOrElseUpdate[String](resource, Duration.Infinity) {
                    ZManaged.fromAutoCloseable(UIO(Source.fromResource(resource))).mapEffect(_.mkString).useNow
                  }
        result <- Command("powershell", script.replace("{0}", a.toString).replace("{1}", a2.toString)).string
      } yield result
    }
}

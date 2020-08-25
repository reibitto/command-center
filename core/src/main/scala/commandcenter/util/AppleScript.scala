package commandcenter.util

import commandcenter.command.cache.InMemoryCache
import zio.blocking._
import zio.clock.Clock
import zio.duration._
import zio.process.Command
import zio.{ RIO, UIO, ZManaged }

import scala.io.Source

object AppleScript {
  def runScript(script: String): RIO[Blocking, String] =
    Command("osascript", "-e", script).string

  def loadFunction0(resource: String): RIO[Blocking with InMemoryCache with Clock, String] =
    for {
      script <- InMemoryCache.getOrElseUpdate[String](s"appleScript/$resource", Duration.Infinity) {
                  ZManaged.fromAutoCloseable(UIO(Source.fromResource(resource))).mapEffect(_.mkString).useNow
                }
      result <- Command("osascript", "-e", script).string
    } yield result

  def loadFunction1[A](resource: String): A => RIO[Blocking with InMemoryCache with Clock, String] =
    p => {
      for {
        script <- InMemoryCache.getOrElseUpdate[String](s"appleScript/$resource", Duration.Infinity) {
                    ZManaged.fromAutoCloseable(UIO(Source.fromResource(resource))).mapEffect(_.mkString).useNow
                  }
        // TODO: Escape properly
        result <- Command("osascript", "-e", script.replace("{0}", p.toString)).string
      } yield result
    }

  def loadFunction2[A, A2](resource: String): (A, A2) => RIO[Blocking with InMemoryCache with Clock, String] =
    (a, a2) => {
      for {
        script <- InMemoryCache.getOrElseUpdate[String](s"appleScript/$resource", Duration.Infinity) {
                    ZManaged.fromAutoCloseable(UIO(Source.fromResource(resource))).mapEffect(_.mkString).useNow
                  }
        // TODO: Escape properly
        result <- Command("osascript", "-e", script.replace("{0}", a.toString).replace("{1}", a2.toString)).string
      } yield result
    }
}

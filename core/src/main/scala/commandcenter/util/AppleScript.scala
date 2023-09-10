package commandcenter.util

import zio.cache.Cache
import zio.process.Command
import zio.Task

object AppleScript {

  def runScript(script: String): Task[String] =
    Command("osascript", "-e", script).string

  def loadFunction0(cache: Cache[String, Nothing, String])(resource: String): Task[String] =
    for {
      script <- cache.get(s"applescript/$resource")
      result <- Command("osascript", "-e", script).string
    } yield result

  def loadFunction1[A](cache: Cache[String, Nothing, String])(resource: String): A => Task[String] =
    p =>
      for {
        script <- cache.get(s"applescript/$resource")
        // TODO: Escape properly
        result <- Command("osascript", "-e", script.replace("{0}", p.toString)).string
      } yield result

  def loadFunction2[A, A2](
      cache: Cache[String, Nothing, String]
  )(resource: String): (A, A2) => Task[String] =
    (a, a2) =>
      for {
        script <- cache.get(s"applescript/$resource")
        // TODO: Escape properly
        result <- Command("osascript", "-e", script.replace("{0}", a.toString).replace("{1}", a2.toString)).string
      } yield result
}

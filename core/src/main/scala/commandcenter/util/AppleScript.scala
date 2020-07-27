package commandcenter.util

import zio.RIO
import zio.blocking._
import zio.process.Command

import scala.io.Source

object AppleScript {
  def runScript(script: String): RIO[Blocking, String] =
    Command("osascript", "-e", script).string

  def loadFunction0(resource: String): RIO[Blocking, String] = {
    // TODO: This is bad. Wrap this in a ZIO. Also make sure we don't read the resource on every call (cache it).
    val script = Source.fromResource(resource).mkString

    Command("osascript", "-e", script).string
  }

  def loadFunction1[A](resource: String): A => RIO[Blocking, String] = {
    val script = Source.fromResource(resource).mkString

    // TODO: Escape properly
    p => Command("osascript", "-e", script.replace("{0}", p.toString)).string
  }

  def loadFunction2[A, A2](resource: String): (A, A2) => RIO[Blocking, String] = {
    val script = Source.fromResource(resource).mkString

    // TODO: Escape properly
    (a, a2) => Command("osascript", "-e", script.replace("{0}", a.toString).replace("{1}", a2.toString)).string
  }
}

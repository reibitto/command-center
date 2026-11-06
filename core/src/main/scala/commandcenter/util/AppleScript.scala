package commandcenter.util

import commandcenter.cache.ZCache
import zio.*
import zio.process.Command

object AppleScript {

  def runScript(script: String): Task[String] =
    Command("osascript", "-e", script).string

  // Escapes a value for use inside a double-quoted AppleScript string
  def escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

  def loadFunction0(cache: ZCache[String, String])(resource: String): Task[String] =
    cache(s"applescript/$resource").flatMap(runScript)

  def loadFunction1[A](cache: ZCache[String, String])(resource: String): A => Task[String] =
    a => run(cache, resource, a)

  def loadFunction2[A, A2](cache: ZCache[String, String])(resource: String): (A, A2) => Task[String] =
    (a, a2) => run(cache, resource, a, a2)

  private def run(cache: ZCache[String, String], resource: String, args: Any*): Task[String] =
    cache(s"applescript/$resource").flatMap(script => runScript(ScriptTemplate.fill(script, args, escape)))
}

private[util] object ScriptTemplate {

  // Replaces `{0}`, `{1}`, ... with the escaped arguments
  def fill(script: String, args: Seq[Any], escape: String => String): String =
    args.zipWithIndex.foldLeft(script) { case (s, (arg, i)) => s.replace(s"{$i}", escape(arg.toString)) }
}

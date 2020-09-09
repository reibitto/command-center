package commandcenter.command.util

import scala.util.Try

object PathUtil {
  val userHomeOpt: Option[String] = Try(System.getProperty("user.home")).toOption

  // TODO: Also have a version that has maxLength and will shorten names that are too long with `..`
  def shorten(path: String): String =
    userHomeOpt match {
      case Some(userHome) if path.startsWith(userHome) =>
        s"~${path.substring(userHome.length)}"

      case _ => path
    }
}

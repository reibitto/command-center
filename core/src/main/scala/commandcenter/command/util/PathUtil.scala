package commandcenter.command.util

import java.io.File
import scala.util.Try

object PathUtil {
  val userHomeOpt: Option[String] = Try(Option(System.getProperty("user.home"))).toOption.flatten

  // TODO: Also have a version that has maxLength and will shorten names that are too long with `..`
  def shorten(path: String): String =
    userHomeOpt match {
      case Some(userHome) if path == userHome || path.startsWith(userHome + File.separator) =>
        s"~${path.substring(userHome.length)}"

      case _ => path
    }
}

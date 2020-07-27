package commandcenter.util

import java.util.Locale

sealed trait OS

object OS {
  case object MacOS   extends OS
  case object Windows extends OS
  case object Linux   extends OS
  case object Other   extends OS

  lazy val os: OS = {
    val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)

    if (osName.contains("mac") || osName.contains("darwin")) {
      OS.MacOS
    } else if (osName.contains("win")) {
      OS.Windows
    } else if (osName.contains("nux")) {
      OS.Linux
    } else {
      OS.Other
    }
  }

}

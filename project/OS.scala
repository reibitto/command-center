import java.util.Locale

sealed trait OS

object OS {
  case object MacOS extends OS
  case object Windows extends OS
  case object Linux extends OS
  final case class Other(name: String) extends OS

  lazy val os: OS = {
    val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)

    if (osName.contains("mac") || osName.contains("darwin"))
      OS.MacOS
    else if (osName.contains("win"))
      OS.Windows
    else if (osName.contains("nux"))
      OS.Linux
    else
      OS.Other(osName)
  }

}

sealed trait OSArch

object OSArch {
  case object AArch64 extends OSArch
  final case class Other(name: String) extends OSArch

  lazy val current: OSArch = {
    val osArch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ENGLISH)

    if (osArch == "aarch64")
      OSArch.AArch64
    else
      OSArch.Other(osArch)
  }
}

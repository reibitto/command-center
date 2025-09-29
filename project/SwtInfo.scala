import sbt.*

final case class SwtInfo(ws: String, os: String, arch: String) {

  def dependencies: Seq[ModuleID] =
    Seq("org.eclipse.platform" % s"org.eclipse.swt.$ws.$os.$arch" % V.swt intransitive ())
}

object SwtInfo {

  lazy val local: SwtInfo =
    OS.os match {
      case OS.Windows =>
        SwtInfo("win32", "win32", "x86_64")

      case OS.MacOS =>
        OSArch.current match {
          case OSArch.AArch64 =>
            SwtInfo("cocoa", "macosx", "aarch64")

          case OSArch.Other(_) =>
            SwtInfo("cocoa", "macosx", "x86_64")
        }

      case OS.Linux =>
        SwtInfo("gtk", "linux", "x86_64")

      case OS.Other(name) =>
        throw new Exception(s"SWT does not support OS '$name'")
    }
}

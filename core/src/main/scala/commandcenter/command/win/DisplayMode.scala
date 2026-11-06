package commandcenter.command.win

import com.sun.jna.{Native, Pointer, WString}
import commandcenter.command.win.GdiDisplay.*
import zio.*

/** Reads and sets a display's resolution/refresh rate via the older GDI
  * display-settings API (see [[GdiDisplay]]), and reads its Windows
  * display-scale percentage.
  *
  * Matching a CCD target to a GDI adapter by comparing `monitorDevicePath`
  * (CCD) against the device interface name from
  * `EnumDisplayDevices(..., EDD_GET_DEVICE_INTERFACE_NAME)` turned out to be
  * unreliable: that specific sub-query kept reporting the previously-active
  * monitor for several hundred ms (observed, reproducibly) after a CCD switch
  * had already succeeded and even after the adapter's own
  * `DISPLAY_DEVICE_ATTACHED_TO_DESKTOP` flag had correctly updated to the new
  * one. So instead, this matches on whichever GDI adapter is currently marked
  * attached - which updates immediately and correctly - and only falls back to
  * the (stale-prone) device path comparison if that's ambiguous (zero or more
  * than one attached adapter, e.g. a multi-monitor arrangement).
  */
object DisplayMode {

  final private case class AdapterInfo(name: String, stateFlags: Int) {
    def attached: Boolean = (stateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP) != 0
    def primary: Boolean = (stateFlags & DISPLAY_DEVICE_PRIMARY_DEVICE) != 0
  }

  /** Resolution, refresh rate and Windows display-scale percentage currently in
    * effect for a display. Scale is read via `GetDeviceCaps(LOGPIXELSX)` on a
    * device context for that specific adapter, which reports the true
    * per-monitor scale regardless of whether this process itself is
    * per-monitor-DPI-aware.
    */
  final case class CurrentMode(width: Int, height: Int, refreshRateHz: Int, scalePercent: Int)

  private def enumAdapters: Task[List[AdapterInfo]] =
    ZIO.attempt {
      Iterator
        .from(0)
        .map { i =>
          val dd = new DISPLAY_DEVICE
          (INSTANCE.EnumDisplayDevicesW(null, i, dd, 0), dd)
        }
        .takeWhile(_._1)
        .map { case (_, dd) => AdapterInfo(Native.toString(dd.DeviceName), dd.StateFlags) }
        .toList
    }

  private def monitorDevicePath(adapterDeviceName: String): Task[Option[String]] =
    ZIO.attempt {
      val dd = new DISPLAY_DEVICE
      val ok = INSTANCE.EnumDisplayDevicesW(new WString(adapterDeviceName), 0, dd, EDD_GET_DEVICE_INTERFACE_NAME)
      if (ok) Some(Native.toString(dd.DeviceID)) else None
    }

  private def findAdapterDeviceName(targetDevicePath: String): Task[Option[String]] =
    for {
      adapters  <- enumAdapters
      withPaths <- ZIO.foreach(adapters)(a => monitorDevicePath(a.name).map(path => (a, path)))
      _         <- ZIO.logDebug(
             s"Looking for GDI device matching `$targetDevicePath` among: " +
               withPaths.map { case (a, path) =>
                 s"${a.name} (attached=${a.attached}, primary=${a.primary}, stateFlags=0x${Integer
                     .toHexString(a.stateFlags)}) -> ${path.getOrElse("(no monitor)")}"
               }
                 .mkString(", ")
           )
      attachedAdapters = withPaths.collect { case (a, _) if a.attached => a }
      result <- attachedAdapters match {
                  case List(only) => ZIO.succeed(Some(only.name))
                  case other      =>
                    ZIO
                      .logDebug(
                        s"${other.length} adapters currently attached (expected exactly 1) - " +
                          "falling back to matching by (possibly stale) monitor device path"
                      )
                      .as(withPaths.collectFirst {
                        case (a, Some(path)) if path.equalsIgnoreCase(targetDevicePath) => a.name
                      })
                }
    } yield result

  private def currentDevMode(adapterName: String): Task[DEVMODE] =
    ZIO.attempt {
      val dm = new DEVMODE
      dm.dmSize = dm.size().toShort
      if (!INSTANCE.EnumDisplaySettingsExW(new WString(adapterName), ENUM_CURRENT_SETTINGS, dm, 0))
        throw new RuntimeException(s"EnumDisplaySettingsEx failed for `$adapterName`")
      dm
    }

  private def dpiScalePercent(adapterName: String): Task[Int] =
    ZIO.attempt {
      val hdc = GDI32.CreateDCW(null, new WString(adapterName), null, Pointer.NULL)
      try {
        val dpi = GDI32.GetDeviceCaps(hdc, LOGPIXELSX)
        Math.round(dpi * 100.0f / 96.0f)
      } finally
        GDI32.DeleteDC(hdc)
    }

  /** The resolution, refresh rate and display scale currently in effect for the
    * display identified by `targetDevicePath` (the CCD target's
    * `monitorDevicePath`) - `None` if it's not currently an active GDI adapter
    * (e.g. powered off).
    */
  def currentMode(targetDevicePath: String): Task[Option[CurrentMode]] =
    for {
      adapterNameOpt <- findAdapterDeviceName(targetDevicePath)
      result         <- ZIO.foreach(adapterNameOpt) { adapterName =>
                  for {
                    dm    <- currentDevMode(adapterName)
                    scale <- dpiScalePercent(adapterName)
                  } yield CurrentMode(dm.dmPelsWidth, dm.dmPelsHeight, dm.dmDisplayFrequency, scale)
                }
    } yield result

  /** Sets the resolution and/or refresh rate for the display identified by
    * `targetDevicePath` - see [[findAdapterDeviceName]] for how that's resolved
    * to a GDI adapter. Whichever of `resolution`/`refreshRateHz` is `None` is
    * left as whatever is currently active.
    *
    * Requires the display to already be active - it won't show up as an
    * attached GDI adapter otherwise - so this is meant to be called as a
    * follow-up right after [[DisplayOutputs.activateOnly]] succeeds, not
    * standalone.
    *
    * Display scale (DPI) is deliberately not settable here: Windows has no
    * supported API to change a monitor's scale percentage at runtime - the only
    * known mechanism is writing an undocumented per-monitor registry value and
    * broadcasting/forcing a re-login for it to take effect, which is too
    * unreliable to script.
    */
  def setMode(targetDevicePath: String, resolution: Option[(Int, Int)], refreshRateHz: Option[Int]): Task[Unit] =
    for {
      adapterNameOpt <- findAdapterDeviceName(targetDevicePath)
      adapterName    <- ZIO
                       .fromOption(adapterNameOpt)
                       .orElseFail(new RuntimeException(s"Could not find a GDI device for `$targetDevicePath`"))
      rc <- ZIO.attempt {
              val wAdapter = new WString(adapterName)
              val dm = new DEVMODE
              dm.dmSize = dm.size().toShort

              if (!INSTANCE.EnumDisplaySettingsExW(wAdapter, ENUM_CURRENT_SETTINGS, dm, 0))
                throw new RuntimeException(s"EnumDisplaySettingsEx failed for `$adapterName`")

              resolution.foreach { case (width, height) =>
                dm.dmPelsWidth = width
                dm.dmPelsHeight = height
                dm.dmFields |= DM_PELSWIDTH | DM_PELSHEIGHT
              }
              refreshRateHz.foreach { hz =>
                dm.dmDisplayFrequency = hz
                dm.dmFields |= DM_DISPLAYFREQUENCY
              }

              INSTANCE.ChangeDisplaySettingsExW(wAdapter, dm, Pointer.NULL, CDS_UPDATEREGISTRY, Pointer.NULL)
            }
      _ <- ZIO.logInfo(
             s"ChangeDisplaySettingsEx($adapterName, " +
               s"${resolution.map { case (w, h) => s"${w}x$h" }.getOrElse("res unchanged")}, " +
               s"${refreshRateHz.map(hz => s"${hz}Hz").getOrElse("Hz unchanged")}) returned $rc" +
               (if (rc == DISP_CHANGE_SUCCESSFUL) "" else " - FAILED")
           )
      _ <- ZIO
             .fail(new RuntimeException(s"ChangeDisplaySettingsEx failed with code $rc for `$adapterName`"))
             .unless(rc == DISP_CHANGE_SUCCESSFUL)
    } yield ()
}

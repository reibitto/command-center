package commandcenter.command.win

import com.sun.jna.{Native, Pointer, WString}
import commandcenter.command.win.GdiDisplay.*
import zio.*

/** Sets a display's refresh rate via the older GDI display-settings API (see
  * [[GdiDisplay]]).
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
object RefreshRate {

  final private case class AdapterInfo(name: String, stateFlags: Int) {
    def attached: Boolean = (stateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP) != 0
    def primary: Boolean = (stateFlags & DISPLAY_DEVICE_PRIMARY_DEVICE) != 0
  }

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

  /** Sets the refresh rate (in Hz) for the display identified by
    * `targetDevicePath` (the CCD target's `monitorDevicePath`) - see
    * [[findAdapterDeviceName]] for how that's resolved to a GDI adapter. Only
    * the refresh rate is changed - whatever resolution and color depth is
    * currently active is preserved as-is.
    *
    * Requires the display to already be active - it won't show up as an
    * attached GDI adapter otherwise - so this is meant to be called as a
    * follow-up right after [[DisplayOutputs.activateOnly]] succeeds, not
    * standalone.
    */
  def setRefreshRate(targetDevicePath: String, hz: Int): Task[Unit] =
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

              dm.dmDisplayFrequency = hz
              dm.dmFields |= DM_DISPLAYFREQUENCY

              INSTANCE.ChangeDisplaySettingsExW(wAdapter, dm, Pointer.NULL, CDS_UPDATEREGISTRY, Pointer.NULL)
            }
      _ <- ZIO.logInfo(
             s"ChangeDisplaySettingsEx($adapterName, ${hz}Hz) returned $rc" +
               (if (rc == DISP_CHANGE_SUCCESSFUL) "" else " - FAILED")
           )
      _ <- ZIO
             .fail(new RuntimeException(s"ChangeDisplaySettingsEx failed with code $rc for `$adapterName` at ${hz}Hz"))
             .unless(rc == DISP_CHANGE_SUCCESSFUL)
    } yield ()
}

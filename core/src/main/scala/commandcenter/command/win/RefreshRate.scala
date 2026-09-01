package commandcenter.command.win

import com.sun.jna.{Native, Pointer, WString}
import commandcenter.command.win.GdiDisplay.*
import zio.*

/** Sets a display's refresh rate via the older GDI display-settings API (see
  * [[GdiDisplay]]), matched to a CCD target by its `monitorDevicePath` (as
  * returned by [[DisplayOutputs]]).
  */
object RefreshRate {

  private def enumAdapterDeviceNames: Task[List[String]] =
    ZIO.attempt {
      Iterator
        .from(0)
        .map { i =>
          val dd = new DISPLAY_DEVICE
          (INSTANCE.EnumDisplayDevicesW(null, i, dd, 0), dd)
        }
        .takeWhile(_._1)
        .map { case (_, dd) => Native.toString(dd.DeviceName) }
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
      adapterNames <- enumAdapterDeviceNames
      withPaths    <- ZIO.foreach(adapterNames)(name => monitorDevicePath(name).map(name -> _))
    } yield withPaths.collectFirst { case (name, Some(path)) if path.equalsIgnoreCase(targetDevicePath) => name }

  /** Sets the refresh rate (in Hz) for the display identified by
    * `targetDevicePath` - the CCD target's `monitorDevicePath`, matched here
    * against the legacy GDI device interface name (obtained via
    * `EnumDisplayDevices` with `EDD_GET_DEVICE_INTERFACE_NAME`, which reports
    * the same device path string CCD does). Only the refresh rate is changed -
    * whatever resolution and color depth is currently active is preserved
    * as-is.
    *
    * Requires the display to already be active - it won't show up in this GDI
    * enumeration otherwise - so this is meant to be called as a follow-up right
    * after [[DisplayOutputs.activateOnly]] succeeds, not standalone.
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

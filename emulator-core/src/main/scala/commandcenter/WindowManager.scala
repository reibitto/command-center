package commandcenter

import com.sun.jna.platform.win32.WinDef.{ HDC, LPARAM, RECT }
import com.sun.jna.platform.win32.WinUser.{ MONITORENUMPROC, MONITORINFO, MONITORINFOEX, WINDOWPLACEMENT }
import com.sun.jna.platform.win32.{ User32, WinUser }
import commandcenter.command.cache.InMemoryCache
import zio.clock.Clock
import zio.{ RIO, Task }

import java.util
import scala.collection.mutable

object WindowManager {
  def centerScreen: Task[Unit] = Task {
    val window  = User32.INSTANCE.GetForegroundWindow()
    val monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST)

    val monitorInfo = new MONITORINFO()
    User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo)

    val windowRect = new RECT()
    User32.INSTANCE.GetWindowRect(window, windowRect)

    val windowWidth  = windowRect.right - windowRect.left
    val windowHeight = windowRect.bottom - windowRect.top

    val monitorWidth  = monitorInfo.rcWork.right - monitorInfo.rcWork.left
    val monitorHeight = monitorInfo.rcWork.bottom - monitorInfo.rcWork.top

    val x = monitorInfo.rcWork.left + (monitorWidth - windowWidth) / 2
    val y = monitorInfo.rcWork.top + (monitorHeight - windowHeight) / 2

    User32.INSTANCE.SetWindowPos(window, null, x, y, windowWidth, windowHeight, WinUser.SWP_NOZORDER)
  }

  def resizeToScreenSize: Task[Unit] = Task {
    val window  = User32.INSTANCE.GetForegroundWindow()
    val monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST)

    val monitorInfo = new MONITORINFO()
    User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo)

    val windowRect = new RECT()
    User32.INSTANCE.GetWindowRect(window, windowRect)

    val monitorWidth  = monitorInfo.rcWork.right - monitorInfo.rcWork.left
    val monitorHeight = monitorInfo.rcWork.bottom - monitorInfo.rcWork.top

    val x = monitorInfo.rcWork.left
    val y = monitorInfo.rcWork.top

    User32.INSTANCE.SetWindowPos(window, null, x, y, monitorWidth, monitorHeight, WinUser.SWP_NOZORDER)
  }

  def minimizeWindow: Task[Unit] = Task {
    val window = User32.INSTANCE.GetForegroundWindow()

    User32.INSTANCE.ShowWindow(window, WinUser.SW_FORCEMINIMIZE)
  }

  def maximizeWindow: Task[Unit] = Task {
    val window = User32.INSTANCE.GetForegroundWindow()

    User32.INSTANCE.ShowWindow(window, WinUser.SW_MAXIMIZE)
  }

  def restoreWindow: Task[Unit] = Task {
    val window = User32.INSTANCE.GetForegroundWindow()

    User32.INSTANCE.ShowWindow(window, WinUser.SW_RESTORE)
  }

  def toggleMaximizeWindow: Task[Unit] = Task {
    val window = User32.INSTANCE.GetForegroundWindow()

    val windowPlacement = new WINDOWPLACEMENT()
    User32.INSTANCE.GetWindowPlacement(window, windowPlacement)

    if ((windowPlacement.flags & WinUser.SW_MAXIMIZE) != 0) {
      User32.INSTANCE.ShowWindow(window, WinUser.SW_RESTORE)
    } else {
      User32.INSTANCE.ShowWindow(window, WinUser.SW_MAXIMIZE)
    }
  }

  def cycleWindowSize(step: Int, name: String)(
    boundsList: Vector[WindowBounds]
  ): RIO[InMemoryCache with Clock, Unit] = {
    val cacheKey = "cycleWindowState"
    for {
      cycleWindowState <- InMemoryCache.get[CycleWindowState](cacheKey).map(_.getOrElse(CycleWindowState(-step, None)))
      newIndex          = if (cycleWindowState.lastAction.contains(name)) {
                            if (step > 0) {
                              (cycleWindowState.index + step) % boundsList.length
                            } else {
                              (boundsList.length - cycleWindowState.index - step) % boundsList.length
                            }
                          } else {
                            0
                          }
      _                <- transform(boundsList(newIndex))
      _                <- InMemoryCache.set(cacheKey, CycleWindowState(newIndex, Some(name)))
    } yield ()
  }

  def transform(bounds: WindowBounds): Task[Unit] = Task {
    val window  = User32.INSTANCE.GetForegroundWindow()
    val monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST)

    val monitorInfo = new MONITORINFO()
    User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo)

    val windowRect = new RECT()
    User32.INSTANCE.GetWindowRect(window, windowRect)

    val monitorWidth  = monitorInfo.rcWork.right - monitorInfo.rcWork.left
    val monitorHeight = monitorInfo.rcWork.bottom - monitorInfo.rcWork.top

    val newWindowWidth  = monitorWidth * (bounds.right - bounds.left)
    val newWindowHeight = monitorHeight * (bounds.bottom - bounds.top)

    val x = monitorInfo.rcWork.left + bounds.left * monitorWidth
    val y = monitorInfo.rcWork.top + bounds.top * monitorHeight

    User32.INSTANCE.SetWindowPos(
      window,
      null,
      x.round.toInt,
      y.round.toInt,
      newWindowWidth.round.toInt,
      newWindowHeight.round.toInt,
      WinUser.SWP_NOZORDER
    )
  }

  def moveToNextDisplay: Task[Unit] = moveToDisplay(1)

  def moveToPreviousDisplay: Task[Unit] = moveToDisplay(-1)

  def moveToDisplay(step: Int): Task[Unit] = Task {
    val window  = User32.INSTANCE.GetForegroundWindow()
    val monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST)

    val windowRect = new RECT()
    User32.INSTANCE.GetWindowRect(window, windowRect)

    val currentMonitor = new MONITORINFOEX()
    User32.INSTANCE.GetMonitorInfo(monitor, currentMonitor)

    val monitors = new mutable.ArrayDeque[MONITORINFOEX]()

    User32.INSTANCE.EnumDisplayMonitors(
      null,
      null,
      new MONITORENUMPROC() {
        override def apply(hMonitor: WinUser.HMONITOR, hdcMonitor: HDC, lprcMonitor: RECT, dwData: LPARAM): Int = {
          val monitorInfo = new MONITORINFOEX()
          User32.INSTANCE.GetMonitorInfo(hMonitor, monitorInfo)
          monitors.append(monitorInfo)
          1
        }
      },
      new LPARAM(0)
    )

    val currentMonitorIndex = monitors.indexWhere(m => util.Arrays.equals(m.szDevice, currentMonitor.szDevice))

    val nextMonitorIndex = if (step > 0) {
      (currentMonitorIndex + step) % monitors.length
    } else {
      (monitors.length - currentMonitorIndex - step) % monitors.length
    }

    val nextMonitor = monitors(nextMonitorIndex)

    val currentMonitorWidth  = currentMonitor.rcWork.right - currentMonitor.rcWork.left
    val currentMonitorHeight = currentMonitor.rcWork.bottom - currentMonitor.rcWork.top

    val boundsLeft   = (windowRect.left - currentMonitor.rcWork.left) / currentMonitorWidth.toDouble
    val boundsRight  = (windowRect.right - currentMonitor.rcWork.left) / currentMonitorWidth.toDouble
    val boundsBottom = (windowRect.bottom - currentMonitor.rcWork.top) / currentMonitorHeight.toDouble
    val boundsTop    = (windowRect.top - currentMonitor.rcWork.top) / currentMonitorHeight.toDouble

    val nextMonitorWidth  = nextMonitor.rcWork.right - nextMonitor.rcWork.left
    val nextMonitorHeight = nextMonitor.rcWork.bottom - nextMonitor.rcWork.top

    val x               = nextMonitor.rcWork.left + boundsLeft * nextMonitorWidth
    val y               = nextMonitor.rcWork.top + boundsTop * nextMonitorHeight
    val newWindowWidth  = (boundsRight - boundsLeft) * nextMonitorWidth
    val newWindowHeight = (boundsBottom - boundsTop) * nextMonitorHeight

    User32.INSTANCE.SetWindowPos(
      window,
      null,
      x.round.toInt,
      y.round.toInt,
      newWindowWidth.round.toInt,
      newWindowHeight.round.toInt,
      WinUser.SWP_NOZORDER
    )

    // HACK: When the DPIs of the monitors are different, Windows seems to automatically resize the window not respecting
    // the width/height sent into SetWindowPos. You can even see that the window is initially the correct size when sent
    // to the second monitor for a split-second. After that Windows resizes it to a different scaled value.
    //
    // A temporary workaround is to call SetWindowPos a 2nd time. Once the window is on the right monitor, SetWindowPos
    // works as expected. Ideally we eventually find a "proper" solution to this.
    User32.INSTANCE.SetWindowPos(
      window,
      null,
      x.round.toInt,
      y.round.toInt,
      newWindowWidth.round.toInt,
      newWindowHeight.round.toInt,
      WinUser.SWP_NOZORDER
    )

  }
}

final case class WindowBounds(left: Double, top: Double, right: Double, bottom: Double)

final case class CycleWindowState(index: Int, lastAction: Option[String])

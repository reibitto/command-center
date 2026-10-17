package commandcenter.util

import com.sun.jna.platform.win32.{Kernel32, User32, WinNT, WinUser}
import com.sun.jna.platform.win32.WinDef.{HDC, HWND, LPARAM, RECT}
import com.sun.jna.platform.win32.WinUser.{MONITORENUMPROC, MONITORINFO, MONITORINFOEX, WINDOWPLACEMENT}
import com.sun.jna.ptr.IntByReference
import com.sun.jna.Pointer
import zio.*

import java.util
import scala.collection.mutable

object WindowManager {

  def centerScreen: Task[Unit] = ZIO.attempt {
    val window = User32.INSTANCE.GetForegroundWindow()
    val monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST)

    val monitorInfo = new MONITORINFO()
    User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo)

    val windowRect = new RECT()
    User32.INSTANCE.GetWindowRect(window, windowRect)

    val windowWidth = windowRect.right - windowRect.left
    val windowHeight = windowRect.bottom - windowRect.top

    val monitorWidth = monitorInfo.rcWork.right - monitorInfo.rcWork.left
    val monitorHeight = monitorInfo.rcWork.bottom - monitorInfo.rcWork.top

    val x = monitorInfo.rcWork.left + (monitorWidth - windowWidth) / 2
    val y = monitorInfo.rcWork.top + (monitorHeight - windowHeight) / 2

    User32.INSTANCE.SetWindowPos(window, null, x, y, windowWidth, windowHeight, WinUser.SWP_NOZORDER)
  }

  def resizeToScreenSize: Task[Unit] = ZIO.attempt {
    val window = User32.INSTANCE.GetForegroundWindow()
    val monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST)

    val monitorInfo = new MONITORINFO()
    User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo)

    val windowRect = new RECT()
    User32.INSTANCE.GetWindowRect(window, windowRect)

    val monitorWidth = monitorInfo.rcWork.right - monitorInfo.rcWork.left
    val monitorHeight = monitorInfo.rcWork.bottom - monitorInfo.rcWork.top

    val x = monitorInfo.rcWork.left
    val y = monitorInfo.rcWork.top

    User32.INSTANCE.SetWindowPos(window, null, x, y, monitorWidth, monitorHeight, WinUser.SWP_NOZORDER)
  }

  def resizeFullHeightMaintainAspectRatio: Task[Unit] = ZIO.attempt {
    val window = User32.INSTANCE.GetForegroundWindow()
    val monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST)

    val monitorInfo = new MONITORINFO()
    User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo)

    val windowRect = new RECT()
    User32.INSTANCE.GetWindowRect(window, windowRect)

    val monitorWidth = monitorInfo.rcWork.right - monitorInfo.rcWork.left
    val monitorHeight = monitorInfo.rcWork.bottom - monitorInfo.rcWork.top

    val windowRatio = (windowRect.right - windowRect.left) / (windowRect.bottom - windowRect.top).toDouble
    val windowWidth = Math.round(monitorHeight * windowRatio).toInt min monitorWidth

    val x = monitorInfo.rcWork.left + (monitorWidth - windowWidth) / 2
    val y = monitorInfo.rcWork.top

    User32.INSTANCE.SetWindowPos(window, null, x, y, windowWidth, monitorHeight, WinUser.SWP_NOZORDER)
  }

  def minimizeWindow: Task[Unit] = ZIO.attempt {
    val window = User32.INSTANCE.GetForegroundWindow()

    User32.INSTANCE.ShowWindow(window, WinUser.SW_FORCEMINIMIZE)
  }

  def maximizeWindow: Task[Unit] = ZIO.attempt {
    val window = User32.INSTANCE.GetForegroundWindow()

    User32.INSTANCE.ShowWindow(window, WinUser.SW_MAXIMIZE)
  }

  def restoreWindow: Task[Unit] = ZIO.attempt {
    val window = User32.INSTANCE.GetForegroundWindow()

    User32.INSTANCE.ShowWindow(window, WinUser.SW_RESTORE)
  }

  def toggleMaximizeWindow: Task[Unit] = ZIO.attempt {
    val window = User32.INSTANCE.GetForegroundWindow()

    val windowPlacement = new WINDOWPLACEMENT()
    User32.INSTANCE.GetWindowPlacement(window, windowPlacement)

    if (windowPlacement.showCmd == WinUser.SW_SHOWMAXIMIZED) {
      User32.INSTANCE.ShowWindow(window, WinUser.SW_RESTORE)
    } else {
      User32.INSTANCE.ShowWindow(window, WinUser.SW_MAXIMIZE)
    }
  }

  def cycleWindowSize(cycleWindowStateRef: Ref[Option[CycleWindowState]])(step: Int, name: String)(
      boundsList: Vector[WindowBounds]
  ): Task[Unit] =
    for {
      // `modify` so that rapid repeated presses can't read the same index
      newIndex <- cycleWindowStateRef.modify { state =>
                    val index = state.collect { case CycleWindowState(i, Some(`name`)) =>
                      Math.floorMod(i + step, boundsList.length)
                    }.getOrElse(0)

                    (index, Some(CycleWindowState(index, Some(name))))
                  }
      _ <- transform(boundsList(newIndex))
    } yield ()

  def transform(bounds: WindowBounds): Task[Unit] = ZIO.attempt {
    val window = User32.INSTANCE.GetForegroundWindow()
    val monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST)

    val windowPlacement = new WINDOWPLACEMENT()
    User32.INSTANCE.GetWindowPlacement(window, windowPlacement)

    // Before transforming the window size, make sure it's not maximized. Otherwise we can get into a weird state (shows
    // up as maximized even though it's not taking up the full screen).
    if (windowPlacement.showCmd == WinUser.SW_SHOWMAXIMIZED || windowPlacement.showCmd == WinUser.SW_MAXIMIZE) {
      User32.INSTANCE.ShowWindow(window, WinUser.SW_RESTORE)
    }

    val monitorInfo = new MONITORINFO()
    User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo)

    val monitorWidth = monitorInfo.rcWork.right - monitorInfo.rcWork.left
    val monitorHeight = monitorInfo.rcWork.bottom - monitorInfo.rcWork.top

    val newWindowWidth = monitorWidth * (bounds.right - bounds.left)
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

  def moveToDisplay(delta: Int): Task[Unit] = ZIO.attempt {
    val window = User32.INSTANCE.GetForegroundWindow()
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

    val nextMonitorIndex = (currentMonitorIndex + delta + monitors.length) % monitors.length

    val nextMonitor = monitors(nextMonitorIndex)

    val currentMonitorWidth = currentMonitor.rcWork.right - currentMonitor.rcWork.left
    val currentMonitorHeight = currentMonitor.rcWork.bottom - currentMonitor.rcWork.top

    val boundsLeft = (windowRect.left - currentMonitor.rcWork.left) / currentMonitorWidth.toDouble
    val boundsRight = (windowRect.right - currentMonitor.rcWork.left) / currentMonitorWidth.toDouble
    val boundsBottom = (windowRect.bottom - currentMonitor.rcWork.top) / currentMonitorHeight.toDouble
    val boundsTop = (windowRect.top - currentMonitor.rcWork.top) / currentMonitorHeight.toDouble

    val nextMonitorWidth = nextMonitor.rcWork.right - nextMonitor.rcWork.left
    val nextMonitorHeight = nextMonitor.rcWork.bottom - nextMonitor.rcWork.top

    val x = nextMonitor.rcWork.left + boundsLeft * nextMonitorWidth
    val y = nextMonitor.rcWork.top + boundsTop * nextMonitorHeight
    val newWindowWidth = (boundsRight - boundsLeft) * nextMonitorWidth
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

  def frontWindow: Task[Option[FrontWindow]] =
    for {
      windowHandleOpt <- ZIO.attempt(Option(User32.INSTANCE.GetForegroundWindow()))
      window          <- ZIO.foreach(windowHandleOpt) { windowHandle =>
                  ZIO.attempt {
                    val title = fromCString(512)(a => User32.INSTANCE.GetWindowText(windowHandle, a, a.length))
                    val processId = new IntByReference()
                    User32.INSTANCE.GetWindowThreadProcessId(windowHandle, processId).toLong

                    FrontWindow(title, processId.getValue, windowHandle)
                  }
                }
    } yield window

  /** Foreground window, or the next window in z-order if Command Center itself
    * is in front.
    */
  def targetWindow: Task[Option[HWND]] =
    for {
      currentPid <- ZIO.succeed(ProcessHandle.current.pid)
      front      <- frontWindow
      window     <- front match {
                  case Some(w) if w.pid != currentPid => ZIO.some(w.windowHandle)
                  case _                              =>
                    topLevelWindows.map(_.map(_.windowHandle).find(w => windowPid(w) != currentPid))
                }
    } yield window

  def windowPid(window: HWND): Long = {
    val processId = new IntByReference()
    User32.INSTANCE.GetWindowThreadProcessId(window, processId)
    processId.getValue.toLong
  }

  def windowStyles(window: HWND): WindowStyles = {
    val rect = new RECT()
    User32.INSTANCE.GetWindowRect(window, rect)

    WindowStyles(
      User32.INSTANCE.GetWindowLong(window, WinUser.GWL_STYLE),
      User32.INSTANCE.GetWindowLong(window, WinUser.GWL_EXSTYLE),
      WindowRect(rect.left, rect.top, rect.right, rect.bottom)
    )
  }

  /** Removes the title bar and frame. If `fillMonitor`, also covers the full
    * monitor (including the taskbar area).
    */
  def makeBorderless(window: HWND, fillMonitor: Boolean): Task[Unit] = ZIO.attempt {
    val WS_EX_DLGMODALFRAME = 0x00000001
    val WS_EX_WINDOWEDGE = 0x00000100
    val WS_EX_CLIENTEDGE = 0x00000200
    val WS_EX_STATICEDGE = 0x00020000

    val windowPlacement = new WINDOWPLACEMENT()
    User32.INSTANCE.GetWindowPlacement(window, windowPlacement)

    // A maximized window keeps its maximized bounds and ignores the new size otherwise
    if (windowPlacement.showCmd == WinUser.SW_SHOWMAXIMIZED) {
      User32.INSTANCE.ShowWindow(window, WinUser.SW_RESTORE)
    }

    // Capture client area before removing the frame so `strip` mode keeps the render size unchanged
    val windowRect = new RECT()
    User32.INSTANCE.GetWindowRect(window, windowRect)
    val clientRect = new RECT()
    User32.INSTANCE.GetClientRect(window, clientRect)

    val style = User32.INSTANCE.GetWindowLong(window, WinUser.GWL_STYLE)
    val exStyle = User32.INSTANCE.GetWindowLong(window, WinUser.GWL_EXSTYLE)

    val removedStyles = WinUser.WS_CAPTION | WinUser.WS_THICKFRAME | WinUser.WS_SYSMENU |
      WinUser.WS_MINIMIZEBOX | WinUser.WS_MAXIMIZEBOX
    val removedExStyles = WS_EX_DLGMODALFRAME | WS_EX_WINDOWEDGE | WS_EX_CLIENTEDGE | WS_EX_STATICEDGE

    User32.INSTANCE.SetWindowLong(window, WinUser.GWL_STYLE, style & ~removedStyles)
    User32.INSTANCE.SetWindowLong(window, WinUser.GWL_EXSTYLE, exStyle & ~removedExStyles)

    val flags = WinUser.SWP_FRAMECHANGED | WinUser.SWP_NOZORDER | WinUser.SWP_NOOWNERZORDER

    if (fillMonitor) {
      val monitor = User32.INSTANCE.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST)
      val monitorInfo = new MONITORINFO()
      User32.INSTANCE.GetMonitorInfo(monitor, monitorInfo)
      val m = monitorInfo.rcMonitor

      User32.INSTANCE.SetWindowPos(window, null, m.left, m.top, m.right - m.left, m.bottom - m.top, flags)
    } else {
      // Frame is symmetric on left/right/bottom; whatever is left over on top is the title bar
      val clientWidth = clientRect.right - clientRect.left
      val clientHeight = clientRect.bottom - clientRect.top
      val border = ((windowRect.right - windowRect.left) - clientWidth) / 2
      val x = windowRect.left + border
      val y = windowRect.bottom - border - clientHeight

      User32.INSTANCE.SetWindowPos(window, null, x, y, clientWidth, clientHeight, flags)
    }
  }

  def restoreWindowStyles(window: HWND, styles: WindowStyles): Task[Unit] = ZIO.attempt {
    User32.INSTANCE.SetWindowLong(window, WinUser.GWL_STYLE, styles.style)
    User32.INSTANCE.SetWindowLong(window, WinUser.GWL_EXSTYLE, styles.exStyle)

    val r = styles.rect
    User32.INSTANCE.SetWindowPos(
      window,
      null,
      r.left,
      r.top,
      r.right - r.left,
      r.bottom - r.top,
      WinUser.SWP_FRAMECHANGED | WinUser.SWP_NOZORDER | WinUser.SWP_NOOWNERZORDER
    )
  }

  def lastErrorCode: Task[Option[Int]] = ZIO.attempt {
    val errorCode = Kernel32.INSTANCE.GetLastError()
    Option.when(errorCode != 0)(errorCode)
  }

  def openProcess(pid: Long): RIO[Scope, WinNT.HANDLE] = ZIO.acquireRelease(
    ZIO.attempt {
      Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_SUSPEND_RESUME, false, pid.toInt)
    }
  ) { handle =>
    ZIO.attempt {
      Kernel32.INSTANCE.CloseHandle(handle)
    }.tapErrorCause { t =>
      ZIO.logWarningCause(s"Could not close handle: $handle", t)
    }.ignore
  }

  def suspendProcess(process: WinNT.HANDLE): Task[Int] = ZIO.attempt {
    NtApi.Lib.INSTANCE.NtSuspendProcess(process)
  }

  def resumeProcess(process: WinNT.HANDLE): Task[Int] = ZIO.attempt {
    NtApi.Lib.INSTANCE.NtResumeProcess(process)
  }

  def commandCenterWindow: Task[Option[HWND]] = ZIO.attempt {
    val currentProcess = ProcessHandle.current()
    var ccWindow: Option[HWND] = None

    User32.INSTANCE.EnumWindows(
      (window: HWND, _: Pointer) => {
        val processId = new IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(window, processId)

        if (processId.getValue == currentProcess.pid()) {
          ccWindow = Some(window)
          false
        } else {
          true
        }
      },
      Pointer.NULL
    )

    ccWindow
  }

  def topLevelWindows: Task[List[TopLevelWindow]] = ZIO.attempt {
    // This list comes from GoToWindow: https://github.com/christianrondeau/GoToWindow/blob/master/GoToWindow.Api/WindowsListFactory.cs
    val ignoredClasses: Set[String] = Set(
      "Button",
      "DV2ControlHost",
      "Frame Alternate Owner",
      "MsgrIMEWindowClass",
      "MultitaskingViewFrame",
      "Shell_TrayWnd",
      "SysShadow",
      "Windows.UI.Core.CoreWindow"
    )

    val WS_EX_TOOLWINDOW = 0x00000080L

    val windows = new mutable.ArrayDeque[TopLevelWindow]()

    User32.INSTANCE.EnumWindows(
      (window: HWND, _: Pointer) => {
        if (
          User32.INSTANCE.IsWindowVisible(window) &&
          (User32.INSTANCE.GetWindowLong(window, WinUser.GWL_EXSTYLE) & WS_EX_TOOLWINDOW) == 0
        ) {
          val className = fromCString(256)(a => User32.INSTANCE.GetClassName(window, a, a.length))

          // TODO: If the className is `ApplicationFrameWindow` that means it's a Windows 10 app. Right now I'm ignoring
          // those but we need to add special logic for it like how GoToWindow does.
          if (!ignoredClasses.contains(className) && className != "ApplicationFrameWindow") {
            val title = fromCString(512)(a => User32.INSTANCE.GetWindowText(window, a, a.length))

            if (!title.isBlank)
              windows.append(TopLevelWindow(title, window))
          }
        }
        true
      },
      Pointer.NULL
    )

    windows.toList
  }

  def giveWindowFocus(window: HWND): Task[Unit] = ZIO.attempt {
    val windowPlacement = new WINDOWPLACEMENT()
    User32.INSTANCE.GetWindowPlacement(window, windowPlacement)

    windowPlacement.showCmd match {
      case WinUser.SW_SHOWMINIMIZED | WinUser.SW_MINIMIZE | WinUser.SW_HIDE =>
        User32.INSTANCE.ShowWindow(window, WinUser.SW_RESTORE)

      case _ => ()
    }

    User32.INSTANCE.SetForegroundWindow(window)
  }

  def switchFocusToPreviousActiveWindow: Task[Unit] =
    for {
      windows <- topLevelWindows
      _       <- ZIO.foreachDiscard(windows.lift(1)) { w =>
             giveWindowFocus(w.windowHandle)
           }
    } yield ()

  def fromCString(bufferSize: Int)(fn: Array[Char] => Int): String = {
    val buffer = Array.ofDim[Char](bufferSize)
    val size = fn(buffer)
    new String(buffer, 0, size)
  }
}

final case class WindowBounds(left: Double, top: Double, right: Double, bottom: Double)

final case class CycleWindowState(index: Int, lastAction: Option[String])

final case class TopLevelWindow(title: String, windowHandle: HWND)

final case class FrontWindow(title: String, pid: Long, windowHandle: HWND)

final case class WindowRect(left: Int, top: Int, right: Int, bottom: Int)

final case class WindowStyles(style: Int, exStyle: Int, rect: WindowRect)

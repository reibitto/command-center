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

    val x = (monitorWidth - windowWidth) / 2
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
      cycleWindowState <- cycleWindowStateRef.get.map(_.getOrElse(CycleWindowState(-step, None)))
      newIndex = if (cycleWindowState.lastAction.contains(name)) {
                   if (step > 0) {
                     (cycleWindowState.index + step) % boundsList.length
                   } else {
                     (boundsList.length - cycleWindowState.index - step) % boundsList.length
                   }
                 } else {
                   0
                 }
      _ <- transform(boundsList(newIndex))
      _ <- cycleWindowStateRef.set(Some(CycleWindowState(newIndex, Some(name))))
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

package commandcenter.emulator.util

import com.sun.jna.{Native, Pointer}
import com.sun.jna.platform.win32.{User32, WinDef, WinUser}
import com.sun.jna.platform.win32.WinDef.{HWND, LPARAM, WPARAM}
import com.sun.jna.win32.StdCallLibrary
import commandcenter.util.OS

import scala.util.Try

// Works around the Microsoft Japanese IME letting the first key after a clipboard change bypass it. Command Center
// commands often copy results to the clipboard, so the first letter typed on the next open came out unconverted.
// With the IME on, letter and digit keys never reach the app as plain key presses, so one that does is re-sent.
object WindowsIme {

  private trait Imm32 extends StdCallLibrary {
    def ImmGetDefaultIMEWnd(hwnd: HWND): HWND
  }

  private lazy val imm32: Option[Imm32] =
    if (OS.os == OS.Windows) Try(Native.load("imm32", classOf[Imm32])).toOption else None

  private val WM_IME_CONTROL = 0x0283
  private val IMC_GETCONVERSIONMODE = 0x0001
  private val IMC_GETOPENSTATUS = 0x0005
  private val IME_CMODE_NATIVE = 0x0001

  // Guards against re-sending the same key in a loop if the re-sent key also bypasses the IME
  private val resendGuardNanos = 500L * 1000 * 1000
  @volatile private var lastResendNanos: Long = 0L

  // Queries the IME through its default window, which works from any thread.
  private def imeControl(windowHandle: Long, command: Int): Option[Long] =
    for {
      lib    <- imm32
      imeWnd <- Option(lib.ImmGetDefaultIMEWnd(new HWND(new Pointer(windowHandle))))
      result  = new WinDef.DWORDByReference()
      ok      = User32.INSTANCE.SendMessageTimeout(
             imeWnd,
             WM_IME_CONTROL,
             new WPARAM(command.toLong),
             new LPARAM(0),
             WinUser.SMTO_ABORTIFHUNG,
             200,
             result
           )
      if ok.longValue() != 0
    } yield result.getValue.longValue()

  // True when the IME is on and converting to Japanese (as opposed to direct alphanumeric input).
  private def isConverting(windowHandle: Long): Boolean =
    imeControl(windowHandle, IMC_GETOPENSTATUS).contains(1L) &&
      imeControl(windowHandle, IMC_GETCONVERSIONMODE).exists(mode => (mode & IME_CMODE_NATIVE) != 0)

  // `virtualKey` is a Windows virtual-key code. Returns true if the key bypassed the IME and was re-sent, in which case
  // the caller must suppress the original key.
  def resendIfBypassed(windowHandle: Long, virtualKey: Int, hasCommandModifier: Boolean): Boolean = {
    val letterOrDigit = (virtualKey >= 'A' && virtualKey <= 'Z') || (virtualKey >= '0' && virtualKey <= '9')

    if (OS.os != OS.Windows || !letterOrDigit || hasCommandModifier || windowHandle == 0L) false
    else if (System.nanoTime() - lastResendNanos < resendGuardNanos) false
    else if (!isConverting(windowHandle)) false
    else {
      lastResendNanos = System.nanoTime()
      // Sent from another thread: injecting input from inside the window thread's key handling loses the key
      val sender = new Thread(() => Try(sendKey(virtualKey)), "ime-key-resend")
      sender.setDaemon(true)
      sender.start()
      true
    }
  }

  def windowHandle(component: java.awt.Component): Long =
    Try(Pointer.nativeValue(Native.getComponentPointer(component))).getOrElse(0L)

  private def sendKey(virtualKey: Int): Unit = {
    // SendInput needs the structures in contiguous memory, hence `toArray`
    val inputs = new WinUser.INPUT().toArray(2).asInstanceOf[Array[WinUser.INPUT]]
    Seq(0, WinUser.KEYBDINPUT.KEYEVENTF_KEYUP).zip(inputs).foreach { case (flags, in) =>
      in.`type` = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong)
      in.input.setType("ki")
      in.input.ki.wVk = new WinDef.WORD(virtualKey.toLong)
      in.input.ki.dwFlags = new WinDef.DWORD(flags.toLong)
    }
    User32.INSTANCE.SendInput(new WinDef.DWORD(inputs.length.toLong), inputs, inputs(0).size())
  }
}

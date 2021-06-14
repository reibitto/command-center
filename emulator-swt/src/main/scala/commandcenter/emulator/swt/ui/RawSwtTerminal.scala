package commandcenter.emulator.swt.ui

import commandcenter.CCConfig
import commandcenter.util.OS
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.graphics.{ Color, Font }
import org.eclipse.swt.layout.{ GridData, GridLayout }
import org.eclipse.swt.widgets.{ Display, Shell, Text }

class RawSwtTerminal(val initialConfig: CCConfig) {
  val display = new Display()
  val shell   = new Shell(display, SWT.MODELESS | SWT.DOUBLE_BUFFERED)

  if (OS.os == OS.Windows) {
    display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme", true)
  }

  val black        = new Color(display, 0, 0, 0)
  val red          = new Color(display, 236, 91, 57)
  val green        = new Color(display, 122, 202, 107)
  val yellow       = new Color(display, 245, 218, 55)
  val blue         = new Color(display, 66, 142, 255)
  val magenta      = new Color(display, 135, 129, 211)
  val cyan         = new Color(display, 22, 180, 236)
  val lightGray    = new Color(display, 100, 100, 100)
  val darkGray     = new Color(display, 50, 50, 50)
  val lightRed     = new Color(display, 255, 135, 119)
  val lightGreen   = new Color(display, 165, 222, 153)
  val lightYellow  = new Color(display, 255, 236, 131)
  val lightBlue    = new Color(display, 75, 149, 255)
  val lightMagenta = new Color(display, 135, 129, 211)
  val lightCyan    = new Color(display, 111, 214, 255)
  val white        = new Color(display, 209, 209, 209)

  def fromFansiColorCode(colorCode: Int): Option[Color] =
    colorCode match {
      case 1  => Some(black)
      case 2  => Some(red)
      case 3  => Some(green)
      case 4  => Some(yellow)
      case 5  => Some(blue)
      case 6  => Some(magenta)
      case 7  => Some(cyan)
      case 8  => Some(lightGray)
      case 9  => Some(darkGray)
      case 10 => Some(lightRed)
      case 11 => Some(lightGreen)
      case 12 => Some(lightYellow)
      case 13 => Some(lightBlue)
      case 14 => Some(lightMagenta)
      case 15 => Some(lightCyan)
      case 16 => Some(white)
      case _  => None
    }

  val font = getPreferredFont(initialConfig.display.fonts)

  val preferredFrameWidth: Int = initialConfig.display.width min display.getBounds.width

  shell.setText("Command Center")
  shell.setMinimumSize(preferredFrameWidth, 0)

  shell.setBackground(black)
  shell.setForeground(white)

  val layout = new GridLayout()
  layout.numColumns = 1

  val inputBoxGridData = new GridData()
  inputBoxGridData.horizontalAlignment = GridData.FILL
  inputBoxGridData.grabExcessHorizontalSpace = true

  shell.setLayout(layout)

  val inputBox = new Text(shell, SWT.NONE)
  inputBox.setFont(font)
  inputBox.setLayoutData(inputBoxGridData)
  inputBox.setBackground(black)
  inputBox.setForeground(white)

  val outputBoxGridData = new GridData()
  outputBoxGridData.exclude = true
  outputBoxGridData.verticalAlignment = GridData.FILL
  outputBoxGridData.horizontalAlignment = GridData.FILL
  outputBoxGridData.grabExcessVerticalSpace = true
  outputBoxGridData.grabExcessHorizontalSpace = true

  val outputBox = new StyledText(shell, SWT.WRAP | SWT.V_SCROLL)
  // Setting `setAlwaysShowScrollBars` to `false` works, but it can cause annoying flickering issues when redrawing in
  // certain environments. We could consider making this a config option and setting the default value to `true`.
  outputBox.setAlwaysShowScrollBars(true)
  outputBox.setFont(font)
  outputBox.setBackground(black)
  outputBox.setForeground(white)
  outputBox.setEditable(false)
  outputBox.setLayoutData(outputBoxGridData)

  shell.pack()

  def loop(): Unit = {
    while (!shell.isDisposed)
      if (!display.readAndDispatch()) display.sleep()

    display.dispose()
  }

  def getPreferredFont(fonts: List[java.awt.Font]): Font = {
    val installedFontNames =
      (display.getFontList(null, false).toSet ++ display.getFontList(null, true).toSet).map(_.getName)

    fonts
      .find(f => installedFontNames.contains(f.getName))
      .map(f => new Font(display, f.getName, f.getSize, SWT.NORMAL))
      .getOrElse {
        new Font(display, "Monospaced", 14, SWT.NORMAL)
      }
  }
}

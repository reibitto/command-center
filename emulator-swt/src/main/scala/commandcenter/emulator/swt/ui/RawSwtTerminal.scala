package commandcenter.emulator.swt.ui

import commandcenter.ui.CCTheme
import commandcenter.util.OS
import commandcenter.CCConfig
import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.graphics.{Color, Font}
import org.eclipse.swt.layout.{GridData, GridLayout}
import org.eclipse.swt.widgets.{Display, Shell, Text}
import org.eclipse.swt.SWT

class RawSwtTerminal(val initialConfig: CCConfig) {
  val display = new Display()
  val shell = new Shell(display, SWT.MODELESS | SWT.DOUBLE_BUFFERED)

  if (OS.os == OS.Windows) {
    display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme", true)
  }

  val theme = CCTheme.default

  private def toSwtColor(c: java.awt.Color): Color = new Color(display, c.getRed, c.getGreen, c.getBlue)

  val background = toSwtColor(theme.background)
  val foreground = toSwtColor(theme.foreground)
  val black = toSwtColor(theme.black)
  val red = toSwtColor(theme.red)
  val green = toSwtColor(theme.green)
  val yellow = toSwtColor(theme.yellow)
  val blue = toSwtColor(theme.blue)
  val magenta = toSwtColor(theme.magenta)
  val cyan = toSwtColor(theme.cyan)
  val lightGray = toSwtColor(theme.lightGray)
  val darkGray = toSwtColor(theme.darkGray)
  val lightRed = toSwtColor(theme.lightRed)
  val lightGreen = toSwtColor(theme.lightGreen)
  val lightYellow = toSwtColor(theme.lightYellow)
  val lightBlue = toSwtColor(theme.lightBlue)
  val lightMagenta = toSwtColor(theme.lightMagenta)
  val lightCyan = toSwtColor(theme.lightCyan)
  val white = toSwtColor(theme.white)

  private val swtColorsByAwtColor: Map[java.awt.Color, Color] = Map(
    theme.black        -> black,
    theme.red          -> red,
    theme.green        -> green,
    theme.yellow       -> yellow,
    theme.blue         -> blue,
    theme.magenta      -> magenta,
    theme.cyan         -> cyan,
    theme.lightGray    -> lightGray,
    theme.darkGray     -> darkGray,
    theme.lightRed     -> lightRed,
    theme.lightGreen   -> lightGreen,
    theme.lightYellow  -> lightYellow,
    theme.lightBlue    -> lightBlue,
    theme.lightMagenta -> lightMagenta,
    theme.lightCyan    -> lightCyan,
    theme.white        -> white
  )

  def fromFansiColorCode(colorCode: Int): Option[Color] =
    theme.fromFansiColorCode(colorCode).flatMap(swtColorsByAwtColor.get)

  val font = getPreferredFont(initialConfig.display.fonts)

  val preferredFrameWidth: Int = initialConfig.display.width min display.getBounds.width

  shell.setText("Command Center")
  shell.setMinimumSize(preferredFrameWidth, 0)

  shell.setBackground(background)
  shell.setForeground(foreground)

  val layout = new GridLayout()
  layout.numColumns = 1

  val inputBoxGridData = new GridData()
  inputBoxGridData.horizontalAlignment = GridData.FILL
  inputBoxGridData.grabExcessHorizontalSpace = true

  shell.setLayout(layout)

  val inputBox = new Text(shell, SWT.NONE)
  inputBox.setFont(font)
  inputBox.setLayoutData(inputBoxGridData)
  inputBox.setBackground(background)
  inputBox.setForeground(foreground)

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
  outputBox.setBackground(background)
  outputBox.setForeground(foreground)
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
      (display.getFontList(null, false).toSet ++ display.getFontList(null, true).toSet).map(_.getName.toLowerCase)

    fonts
      .find(f => installedFontNames.contains(f.getName.toLowerCase))
      .map(f => new Font(display, f.getName, f.getSize, SWT.NORMAL))
      .getOrElse {
        new Font(display, "Monospaced", 14, SWT.NORMAL)
      }
  }
}

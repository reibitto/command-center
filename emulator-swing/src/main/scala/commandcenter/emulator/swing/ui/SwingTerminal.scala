package commandcenter.emulator.swing.ui

import commandcenter.*
import commandcenter.command.*
import commandcenter.emulator.swing.event.KeyboardShortcutUtil
import commandcenter.emulator.util.AnsiRun
import commandcenter.tools.Tools
import commandcenter.ui.{CCTheme, SearchDebouncer}
import commandcenter.util.{Debouncer, OS, WindowManager}
import commandcenter.util.RuntimeExtensions.*
import commandcenter.view.{Rendered, Style}
import commandcenter.CCRuntime.Env
import zio.*

import java.awt.*
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.plaf.basic.BasicScrollBarUI
import javax.swing.text.{DefaultStyledDocument, SimpleAttributeSet, StyleConstants, StyleContext}

final case class SwingTerminal(
    commandCursorRef: Ref[Int],
    searchResultsRef: Ref[SearchResults[Any]],
    searchDebouncer: Debouncer[Env, Nothing, Unit],
    closePromise: Promise[Nothing, Unit]
)(implicit runtime: Runtime[Env])
    extends BaseGuiTerminal {
  val terminalType: TerminalType = TerminalType.Swing

  val theme = CCTheme.default
  val document = new DefaultStyledDocument
  val context = new StyleContext
  val frame = new JFrame("Command Center")

  val preferredFont = runtime.unsafeRunOrThrow(getPreferredFont)

  frame.setBackground(theme.background)
  frame.setFocusable(false)
  frame.setUndecorated(true)
  frame.getContentPane.setLayout(new BorderLayout())
  frame.getRootPane.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, CCTheme.default.darkGray))

  val inputTextField = new ZTextField
  inputTextField.setFont(preferredFont)
  inputTextField.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10))
  inputTextField.setBackground(theme.background)
  inputTextField.setForeground(theme.foreground)
  inputTextField.setCaretColor(theme.foreground)
  // Enable ability to detect Tab key presses
  inputTextField.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, java.util.Collections.emptySet())
  frame.getContentPane.add(inputTextField, BorderLayout.NORTH)

  val outputTextPane = new JTextPane(document)
  outputTextPane.setFont(preferredFont)
  outputTextPane.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 10))

  outputTextPane.setBackground(theme.background)
  outputTextPane.setForeground(theme.foreground)
//  outputTextPane.setCaretColor(Color.RED) // TODO: Make caret color configurable
  outputTextPane.setEditable(false)

  val outputScrollPane = new JScrollPane(
    outputTextPane,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  ) {

    override def getPreferredSize: Dimension =
      runtime.unsafeRunOrThrow {
        for {
          config              <- Conf.config
          preferredFrameWidth <- getPreferredFrameWidth
          searchResults       <- searchResultsRef.get
          height = if (searchResults.previews.isEmpty) 0
                   else
                     outputTextPane.getPreferredSize.height min config.display.maxHeight
        } yield new Dimension(preferredFrameWidth, height)
      }
  }
  outputScrollPane.setBorder(BorderFactory.createEmptyBorder())

  outputScrollPane.getVerticalScrollBar.setUI(new BasicScrollBarUI() {

    val emptyButton: JButton = {
      val button = new JButton()
      button.setPreferredSize(new Dimension(0, 0))
      button.setMinimumSize(new Dimension(0, 0))
      button.setMaximumSize(new Dimension(0, 0))
      button
    }

    override def createDecreaseButton(orientation: Int): JButton = emptyButton
    override def createIncreaseButton(orientation: Int): JButton = emptyButton

    override protected def configureScrollBarColors(): Unit = {
      thumbColor = new Color(50, 50, 50)
      thumbDarkShadowColor = new Color(30, 30, 30)
      thumbHighlightColor = new Color(90, 90, 90)
      thumbLightShadowColor = new Color(70, 70, 70)
      trackColor = Color.BLACK
      trackHighlightColor = Color.LIGHT_GRAY
    }
  })

  frame.getContentPane.add(outputScrollPane, BorderLayout.CENTER)

  inputTextField.addOnChangeListener(_ => onInputChanged(inputTextField.getText))

  override protected def render(searchResults: SearchResults[Any]): URIO[Env, Unit] =
    commandCursorRef.get.map { commandCursor =>
      if (frame.isVisible) { // If the frame isn't visible, trying to insert into the document will throw an exception
        SwingUtilities.invokeLater { () =>
          document.remove(0, document.getLength)

          var scrollToPosition: Int = 0

          def renderBar(rowIndex: Int): Unit = {
            val barStyle = new SimpleAttributeSet()

            if (rowIndex == commandCursor)
              StyleConstants.setBackground(barStyle, CCTheme.default.green)
            else
              StyleConstants.setBackground(barStyle, CCTheme.default.darkGray)

            document.insertString(document.getLength, " ", barStyle)
            document.insertString(document.getLength, " ", null)
          }

          searchResults.rendered.zipWithIndex.foreach { case (r, row) =>
            r match {
              case Rendered.Styled(segments) =>
                renderBar(row)

                if (row < commandCursor)
                  scrollToPosition += segments.map(_.text.length).sum + 3

                segments.foreach { styledText =>
                  val style = new SimpleAttributeSet()

                  styledText.styles.foreach {
                    case Style.Bold                   => StyleConstants.setBold(style, true)
                    case Style.Underline              => StyleConstants.setUnderline(style, true)
                    case Style.Italic                 => StyleConstants.setItalic(style, true)
                    case Style.ForegroundColor(color) => StyleConstants.setForeground(style, color)
                    case Style.BackgroundColor(color) => StyleConstants.setBackground(style, color)
                    case Style.FontFamily(fontFamily) => StyleConstants.setFontFamily(style, fontFamily)
                    case Style.FontSize(fontSize)     => StyleConstants.setFontSize(style, fontSize)
                  }

                  document.insertString(document.getLength, styledText.text, style)
                }

                if (row < searchResults.rendered.length - 1)
                  document.insertString(document.getLength, "\n", null)

              case ar: Rendered.Ansi =>
                renderBar(row)

                if (row < commandCursor)
                  scrollToPosition += ar.ansiStr.length + 3

                val renderStr = if (row < searchResults.rendered.length - 1) ar.ansiStr ++ "\n" else ar.ansiStr

                AnsiRun.runs(renderStr).foreach { run =>
                  val foreground = theme.fromFansiColorCode(run.foreground)
                  val background = theme.fromFansiColorCode(run.background)

                  // Don't bother wastefully creating a style object
                  val style =
                    if (foreground.isEmpty && background.isEmpty) null
                    else {
                      val style = new SimpleAttributeSet()
                      foreground.foreach(StyleConstants.setForeground(style, _))
                      background.foreach(StyleConstants.setBackground(style, _))
                      style
                    }

                  document.insertString(document.getLength, run.text, style)
                }
            }
          }

          outputTextPane.setCaretPosition(scrollToPosition)

          frame.pack()
        }
      } else {
        SwingUtilities.invokeLater { () =>
          document.remove(0, document.getLength)
          frame.pack()
        }
      }
    }

  def reset: URIO[Env, Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- ZIO.succeed {
             inputTextField.setText("")
             document.remove(0, document.getLength)
           }
      _ <- searchResultsRef.set(SearchResults.empty)
    } yield ()

  inputTextField.addZKeyListener(new ZKeyAdapter {

    override def keyReleased(e: KeyEvent): URIO[Env, Unit] =
      e.getKeyCode match {
        case KeyEvent.VK_ENTER  => onRelease(runSelected)
        case KeyEvent.VK_ESCAPE => onRelease(resetAndHide)
        case _                  => ZIO.unit
      }

    override def keyPressed(e: KeyEvent): URIO[Env, Unit] =
      e.getKeyCode match {
        case KeyEvent.VK_ENTER  => onPress(runSelected)
        case KeyEvent.VK_ESCAPE => onPress(resetAndHide)

        case KeyEvent.VK_DOWN =>
          e.consume() // Not ideal to have it outside the for-comprehension, but wrapping this in UIO will not work

          for {
            previousResults <- searchResultsRef.get
            previousCursor  <-
              commandCursorRef.getAndUpdate(cursor => (cursor + 1) min (previousResults.previews.length - 1))
            // TODO: Add `renderSelectionCursor` optimization here too (refer to SwtTerminal)
            _ <- render(previousResults).when(previousCursor < previousResults.previews.length - 1)
          } yield ()

        case KeyEvent.VK_UP =>
          e.consume() // Not ideal to have it outside the for-comprehension, but wrapping this in UIO will not work

          for {
            previousResults <- searchResultsRef.get
            previousCursor  <- commandCursorRef.getAndUpdate(cursor => (cursor - 1) max 0)
            // TODO: Add `renderSelectionCursor` optimization here too (refer to SwtTerminal)
            _ <- render(previousResults).when(previousCursor > 0)
          } yield ()

        case _ => runShortcut(KeyboardShortcutUtil.fromKeyEvent(e))
      }
  })

  frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

  frame.setMinimumSize(new Dimension(runtime.unsafeRunOrThrow(getPreferredFrameWidth), 20))
  frame.pack()

  def open: RIO[Env, Unit] =
    for {
      conf <- Conf.config
      _    <- ZIO.attempt {
             val bounds =
               GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice.getDefaultConfiguration.getBounds

             val x = (bounds.width - frame.getWidth) / 2

             frame.setLocation(x + conf.display.offsetX, conf.display.offsetY)
             frame.setVisible(true)
           }
    } yield ()

  def hide: URIO[Conf, Unit] =
    for {
      keepOpen <- Conf.get(_.general.keepOpen)
      _        <- if (keepOpen)
             WindowManager.switchFocusToPreviousActiveWindow.ignore
           else
             ZIO.succeed(frame.setVisible(false))
    } yield ()

  def activate: RIO[Tools, Unit] =
    OS.os match {
      case OS.MacOS => Tools.activate
      case _        =>
        ZIO.succeed {
          frame.toFront()
          frame.requestFocus()
          inputTextField.requestFocusInWindow()
        }
    }

  def opacity: RIO[Env, Float] = ZIO.succeed(frame.getOpacity)

  def setOpacity(opacity: Float): RIO[Env, Unit] =
    ZIO.attempt(frame.setOpacity(opacity)).whenZIO(isOpacitySupported).unit

  def isOpacitySupported: URIO[Env, Boolean] =
    ZIO
      .attempt(
        GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice
          .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)
      )
      .orElseSucceed(false)

  def size: RIO[Env, Dimension] = ZIO.succeed(frame.getSize)

  def setSize(width: Int, maxHeight: Int): RIO[Env, Unit] = ZIO.unit

  def reload: RIO[Env, Unit] =
    for {
      config <- Conf.reload
      _      <- setOpacity(config.display.opacity)
      font   <- getPreferredFont
      _      <- ZIO.succeed {
             SwingUtilities.invokeLater { () =>
               inputTextField.setFont(font)
               outputTextPane.setFont(font)
             }
           }
    } yield ()

  def getPreferredFont: URIO[Conf, Font] = {
    def fallbackFont = new Font("Monospaced", Font.PLAIN, 18)

    (for {
      fonts              <- Conf.get(_.display.fonts)
      installedFontNames <-
        ZIO.attempt(
          GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames.map(_.toLowerCase).toSet
        )
      font = fonts.find(f => installedFontNames.contains(f.getName.toLowerCase)).getOrElse(fallbackFont)
    } yield font).orElseSucceed(fallbackFont)
  }

  def getPreferredFrameWidth: URIO[Conf, Int] =
    for {
      width       <- Conf.get(_.display.width)
      screenWidth <-
        ZIO
          .attempt(
            GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice.getDefaultConfiguration.getBounds.width
          )
          .orElseSucceed(width)
    } yield width min screenWidth
}

object SwingTerminal {

  def create: RIO[Scope & Env, SwingTerminal] =
    for {
      runtime          <- ZIO.runtime[Env]
      searchDebouncer  <- SearchDebouncer.make
      commandCursorRef <- Ref.make(0)
      searchResultsRef <- Ref.make(SearchResults.empty[Any])
      closePromise     <- Promise.make[Nothing, Unit]
      swingTerminal    <-
        ZIO.acquireRelease(
          ZIO.succeed(new SwingTerminal(commandCursorRef, searchResultsRef, searchDebouncer, closePromise)(runtime))
        )(t => ZIO.succeed(t.frame.dispose()))
      _ <- swingTerminal.init
    } yield swingTerminal
}

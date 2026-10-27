package commandcenter.emulator.swing.ui

import commandcenter.*
import commandcenter.command.*
import commandcenter.emulator.swing.event.KeyboardShortcutUtil
import commandcenter.emulator.util.{Lists, WindowsIme}
import commandcenter.event.{KeyboardShortcut, KeyModifier}
import commandcenter.locale.Language
import commandcenter.tools.Tools
import commandcenter.ui.CCTheme
import commandcenter.util.{Debouncer, OS, WindowManager}
import commandcenter.view.{Rendered, Style}
import commandcenter.CCRuntime.Env
import zio.*

import java.awt.*
import java.awt.event.{InputEvent, KeyAdapter, KeyEvent}
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

  val preferredFont = Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(getPreferredFont).getOrThrow()
  }

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
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.run {
          for {
            config              <- Conf.config
            preferredFrameWidth <- getPreferredFrameWidth
            searchResults       <- searchResultsRef.get
            height = if (searchResults.previews.isEmpty) 0
                     else
                       outputTextPane.getPreferredSize.height min config.display.maxHeight
          } yield new Dimension(preferredFrameWidth, height)
        }.getOrThrow()
      }
  }
  outputScrollPane.setBorder(BorderFactory.createEmptyBorder())
  outputScrollPane.getViewport.setBackground(theme.background)
  frame.getContentPane.setBackground(theme.background)

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

  inputTextField.addOnChangeListener { e =>
    val searchTerm = inputTextField.getText
    val context = CommandContext(Language.detect(searchTerm), SwingTerminal.this, 1.0)

    for {
      config        <- Conf.config
      searchResults <- searchResultsRef.get
      // This is an optimization but it also helps with certain IMEs where they resend all the changes when
      // exiting out of composition mode (committing the changes).
      sameAsLast = searchResults.searchTerm == searchTerm && searchTerm.nonEmpty
      _ <- searchDebouncer(
             Command
               .search(config.commands, config.aliases, searchTerm, context)
               .tap(r => commandCursorRef.set(0) *> searchResultsRef.set(r) *> render(r))
               .unless(sameAsLast)
               .unit
           )
    } yield ()
  }

  def init: RIO[Env, Unit] =
    for {
      opacity <- Conf.get(_.display.opacity)
      _       <- setOpacity(opacity)
    } yield ()

  override protected def render(searchResults: SearchResults[Any]): URIO[Env, Unit] =
    for {
      commandCursor <- commandCursorRef.get
    } yield
      if (frame.isVisible) { // If the frame isn't visible, trying to insert into the document will throw an exception
        SwingUtilities.invokeLater { () =>
          def colorMask(width: Int): Long = ~0L >>> (64 - width)

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

                val plainText = renderStr.plainText
                var i: Int = 0
                Lists.groupConsecutive(renderStr.getColors.toList).foreach { c =>
                  val s = plainText.substring(i, i + c.length)

                  i += c.length

                  val ansiForeground = (c.head >>> fansi.Color.offset) & colorMask(fansi.Color.width)
                  val ansiBackground = (c.head >>> fansi.Back.offset) & colorMask(fansi.Back.width)

                  val awtForegroundOpt = CCTheme.default.fromFansiColorCode(ansiForeground.toInt)
                  val awtBackgroundOpt = CCTheme.default.fromFansiColorCode(ansiBackground.toInt)

                  val style = (awtForegroundOpt, awtBackgroundOpt) match {
                    case (None, None) =>
                      // Don't bother wastefully creating a StyleRange object
                      null

                    case _ =>
                      val style = new SimpleAttributeSet()

                      awtForegroundOpt.foreach(StyleConstants.setForeground(style, _))
                      awtBackgroundOpt.foreach(StyleConstants.setBackground(style, _))

                      style
                  }

                  document.insertString(document.getLength, s, style)
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

  def reset: URIO[Env, Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- ZIO.succeed {
             SwingUtilities.invokeLater { () =>
               inputTextField.setText("")
               document.remove(0, document.getLength)
             }
           }
      _ <- searchResultsRef.set(SearchResults.empty)
    } yield ()

  inputTextField.addZKeyListener(new ZKeyAdapter {
    // Enter/Escape pressed outside IME composition. The IME can swallow the press (committing or cancelling the
    // composition) but still let the release through, so releases only act on a press recorded here. EDT-only.
    private var pressedActionKey: Option[Int] = None
    // Modifiers held when Enter was pressed, so they still count if released before Enter
    private var enterModifiers: Set[KeyModifier] = Set.empty

    override def keyReleased(e: KeyEvent): URIO[Env, Unit] = {
      val pressed = pressedActionKey.contains(e.getKeyCode)
      if (pressed) pressedActionKey = None

      e.getKeyCode match {
        case KeyEvent.VK_ENTER if pressed =>
          runSelected(enterModifiers).whenZIO(Conf.get(_.general.hideOnKeyRelease)).unit

        case KeyEvent.VK_ESCAPE if pressed =>
          resetAndHide.whenZIO(Conf.get(_.general.hideOnKeyRelease)).unit

        case _ => ZIO.unit
      }
    }

    override def keyPressed(e: KeyEvent): URIO[Env, Unit] =
      e.getKeyCode match {
        case KeyEvent.VK_ENTER | KeyEvent.VK_ESCAPE if inputTextField.isComposing =>
          ZIO.unit

        case KeyEvent.VK_ENTER =>
          pressedActionKey = Some(KeyEvent.VK_ENTER)
          enterModifiers = KeyboardShortcutUtil.fromKeyEvent(e).modifiers
          runSelected(enterModifiers).unlessZIO(Conf.get(_.general.hideOnKeyRelease)).unit

        case KeyEvent.VK_ESCAPE =>
          pressedActionKey = Some(KeyEvent.VK_ESCAPE)
          resetAndHide.unlessZIO(Conf.get(_.general.hideOnKeyRelease)).unit

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

        case _ =>
          for {
            previousResults <- searchResultsRef.get
            shortcutPressed = KeyboardShortcutUtil.fromKeyEvent(e)
            eligibleResults = previousResults.previews.filter { p =>
                                p.shortcuts.contains(shortcutPressed)
                              }
            bestMatch = eligibleResults.maxByOption(_.score)
            _ <- bestMatch match {
                   case Some(preview) => runPreview(preview)
                   case None          => runGlobalShortcut(shortcutPressed)
                 }
          } yield ()
      }
  })

  // Falls back to a command's own configured `shortcuts` even when the input textbox is empty.
  private def runGlobalShortcut(shortcutPressed: KeyboardShortcut): URIO[Env, Unit] =
    if (shortcutPressed.isEmpty)
      ZIO.unit
    else
      for {
        config <- Conf.config
        commandOpt = config.commands.find(_.shortcuts.contains(shortcutPressed))
        cmdContext = CommandContext(Language.default, SwingTerminal.this, 1.0)
        previewOpt <- ZIO.foreach(commandOpt)(Command.searchByCommandName(_, cmdContext)).map(_.flatten)
        _          <- ZIO.foreachDiscard(previewOpt)(runPreview)
      } yield ()

  private def runPreview(preview: PreviewResult[Any]): URIO[Env, Unit] =
    for {
      _ <- hide.when(preview.runOption != RunOption.RemainOpen)
      _ <- preview.onRunSandboxedLogged.forkDaemon
      _ <- reset.when(preview.runOption != RunOption.RemainOpen)
    } yield ()

  private def hwnd: Long = WindowsIme.windowHandle(frame)

  if (OS.os == OS.Windows) {
    inputTextField.addKeyListener(new KeyAdapter {
      private var suppressTyped = false

      override def keyPressed(e: KeyEvent): Unit = {
        val commandModifier =
          (e.getModifiersEx & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0
        // Java letter and digit key codes are the same as Windows virtual-key codes
        if (!inputTextField.isComposing && WindowsIme.resendIfBypassed(hwnd, e.getKeyCode, commandModifier)) {
          e.consume()
          suppressTyped = true
        }
      }

      override def keyTyped(e: KeyEvent): Unit =
        if (suppressTyped) {
          suppressTyped = false
          e.consume()
        }
    })
  }

  frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

  frame.setMinimumSize(
    new Dimension(
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.run(getPreferredFrameWidth).getOrThrow()
      },
      20
    )
  )
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
             WindowManager.switchFocusToPreviousActiveWindow.when(keepOpen).ignore
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
    def fallbackFont = new Font("Monospaced", Font.PLAIN, 14)

    // Config sizes are points (as SWT treats them), but AWT treats font sizes as pixels at 72 DPI. Windows and Linux
    // use 96 logical DPI, so convert to match SWT's rendered size.
    def pointsToAwtSize(font: Font): Font =
      if (OS.os == OS.MacOS) font
      else font.deriveFont(math.round(font.getSize2D * 96f / 72f).toFloat)

    (for {
      fonts              <- Conf.get(_.display.fonts)
      installedFontNames <-
        ZIO.attempt(
          GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames.map(_.toLowerCase).toSet
        )
      font = fonts.find(f => installedFontNames.contains(f.getName.toLowerCase)).getOrElse(fallbackFont)
    } yield pointsToAwtSize(font)).orElseSucceed(pointsToAwtSize(fallbackFont))
  }

  def getPreferredFrameWidth: URIO[Conf, Int] =
    for {
      width       <- Conf.get(_.display.width)
      screenWidth <-
        ZIO
          .attempt(
            GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice.getDefaultConfiguration.getBounds.width
          )
          .orElse(ZIO.succeed(width))
    } yield width min screenWidth
}

object SwingTerminal {

  def create: RIO[Scope & Env, SwingTerminal] =
    for {
      runtime         <- ZIO.runtime[Env]
      generalConfig   <- Conf.get(_.general)
      searchDebouncer <- Debouncer.make[Env, Nothing, Unit](
                           generalConfig.debounceDelay,
                           Some(generalConfig.opTimeoutOrDefault)
                         )
      commandCursorRef <- Ref.make(0)
      searchResultsRef <- Ref.make(SearchResults.empty[Any])
      closePromise     <- Promise.make[Nothing, Unit]
      swingTerminal    <-
        ZIO.acquireRelease(
          ZIO.succeed(new SwingTerminal(commandCursorRef, searchResultsRef, searchDebouncer, closePromise)(runtime))
        )(t =>
          // Don't block on dispose: during JVM shutdown the AWT toolkit thread is gone, so a blocking dispose hangs
          // the shutdown hook (e.g. on Ctrl-C) forever.
          ZIO.succeed(SwingUtilities.invokeLater(() => t.frame.dispose()))
        )
      _ <- swingTerminal.init
    } yield swingTerminal
}

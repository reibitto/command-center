package commandcenter.emulator.swing.ui

import commandcenter.*
import commandcenter.command.*
import commandcenter.emulator.swing.event.KeyboardShortcutUtil
import commandcenter.emulator.util.Lists
import commandcenter.locale.Language
import commandcenter.tools.Tools
import commandcenter.ui.CCTheme
import commandcenter.util.Debouncer
import commandcenter.util.OS
import commandcenter.util.WindowManager
import commandcenter.view.Rendered
import commandcenter.view.Style
import commandcenter.CCRuntime.Env
import zio.*
import zio.stream.ZSink

import java.awt.*
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.plaf.basic.BasicScrollBarUI
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext

final case class SwingTerminal(
    commandCursorRef: Ref[Int],
    searchResultsRef: Ref[SearchResults[Any]],
    searchDebouncer: Debouncer[Env, Nothing, Unit],
    closePromise: Promise[Nothing, Unit]
)(implicit runtime: Runtime[Env])
    extends GuiTerminal {
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

  private def render(searchResults: SearchResults[Any]): UIO[Unit] =
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

                segments.foreach { styledText =>
                  val style = new SimpleAttributeSet()

                  styledText.styles.foreach {
                    case Style.Bold                   => StyleConstants.setBold(style, true)
                    case Style.Underline              => StyleConstants.setUnderline(style, true)
                    case Style.Italic                 => StyleConstants.setItalic(style, true)
                    case Style.ForegroundColor(color) => StyleConstants.setForeground(style, color)
                    case Style.BackgroundColor(color) => StyleConstants.setForeground(style, color)
                    case Style.FontFamily(fontFamily) => StyleConstants.setFontFamily(style, fontFamily)
                    case Style.FontSize(fontSize)     => StyleConstants.setFontSize(style, fontSize)
                  }

                  if (row < commandCursor)
                    scrollToPosition += styledText.text.length + 3

                  document.insertString(document.getLength, styledText.text, style)
                }

                if (row < searchResults.rendered.length - 1)
                  document.insertString(document.getLength, "\n", null)

              case ar: Rendered.Ansi =>
                renderBar(row)

                if (row < commandCursor)
                  scrollToPosition += ar.ansiStr.length + 3

                val renderStr = if (row < searchResults.rendered.length - 1) ar.ansiStr ++ "\n" else ar.ansiStr

                var i: Int = 0
                Lists.groupConsecutive(renderStr.getColors.toList).foreach { c =>
                  val s = renderStr.plainText.substring(i, i + c.length)

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

  def reset: UIO[Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- ZIO.succeed {
             inputTextField.setText("")
             document.remove(0, document.getLength)
           }
      _ <- searchResultsRef.set(SearchResults.empty)
    } yield ()

  def runIndex(results: SearchResults[Any], cursorIndex: Int): RIO[Env, Option[PreviewResult[Any]]] =
    results.previews.lift(cursorIndex) match {
      case None =>
        for {
          _ <- hide
          _ <- deactivate.ignore
        } yield None

      case previewOpt @ Some(preview) =>
        for {
          _ <- (hide *> deactivate.ignore).when(preview.runOption != RunOption.RemainOpen)
          _ <- preview.moreResults match {
                 case MoreResults.Remaining(p @ PreviewResults.Paginated(rs, _, pageSize, totalRemaining))
                     if totalRemaining.forall(_ > 0) =>
                   for {
                     _ <- preview.onRunSandboxedLogged.forkDaemon
                     (results, restStream) <- Scope.global.use {
                                                rs.peel(ZSink.take[PreviewResult[Any]](pageSize))
                                                  .mapError(_.toThrowable)
                                              }
                     _ <- showMore(
                            results,
                            preview.moreResults(
                              MoreResults.Remaining(
                                p.copy(
                                  results = restStream,
                                  totalRemaining = p.totalRemaining.map(_ - pageSize)
                                )
                              )
                            ),
                            pageSize
                          )
                   } yield ()

                 case _ =>
                   preview.onRunSandboxedLogged.forkDaemon *> reset
               }
        } yield previewOpt
    }

  def showMore[A](
      moreResults: Chunk[PreviewResult[A]],
      previewSource: PreviewResult[A],
      pageSize: Int
  ): RIO[Env, Unit] =
    for {
      cursorIndex <- commandCursorRef.get
      searchResults <- searchResultsRef.updateAndGet { results =>
                         val (front, back) = results.previews.splitAt(cursorIndex)

                         val previews = if (moreResults.length < pageSize) {
                           front ++ moreResults ++ back.tail
                         } else {
                           front ++ moreResults ++ Chunk.single(previewSource) ++ back.tail
                         }

                         results.copy(previews = previews)
                       }
      _ <- render(searchResults)
    } yield ()

  private def runSelected: ZIO[Env, Nothing, Unit] =
    for {
      _               <- searchDebouncer.triggerNowAwait
      previousResults <- searchResultsRef.get
      cursorIndex     <- commandCursorRef.get
      resultOpt       <- runIndex(previousResults, cursorIndex).catchAll(_ => ZIO.none)
      _ <- ZIO.whenCase(resultOpt.map(_.runOption)) { case Some(RunOption.Exit) =>
             ZIO.succeed(java.lang.System.exit(0)).forkDaemon
           }
    } yield ()

  private def resetAndHide: ZIO[Env, Nothing, Unit] =
    for {
      _ <- hide
      _ <- deactivate.ignore
      _ <- reset
    } yield ()

  inputTextField.addZKeyListener(new ZKeyAdapter {

    override def keyReleased(e: KeyEvent): URIO[Env, Unit] =
      e.getKeyCode match {
        case KeyEvent.VK_ENTER =>
          runSelected.whenZIO(Conf.get(_.general.hideOnKeyRelease)).unit

        case KeyEvent.VK_ESCAPE =>
          resetAndHide.whenZIO(Conf.get(_.general.hideOnKeyRelease)).unit

        case _ => ZIO.unit
      }

    override def keyPressed(e: KeyEvent): URIO[Env, Unit] =
      e.getKeyCode match {
        case KeyEvent.VK_ENTER =>
          runSelected.unlessZIO(Conf.get(_.general.hideOnKeyRelease)).unit

        case KeyEvent.VK_ESCAPE =>
          resetAndHide.unlessZIO(Conf.get(_.general.hideOnKeyRelease)).unit

        case KeyEvent.VK_DOWN =>
          e.consume() // Not ideal to have it outside the for-comprehension, but wrapping this in UIO will not work

          for {
            previousResults <- searchResultsRef.get
            previousCursor <-
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
            _ <- ZIO.foreachDiscard(bestMatch) { preview =>
                   for {
                     _ <- hide.when(preview.runOption != RunOption.RemainOpen)
                     _ <- preview.onRunSandboxedLogged.forkDaemon
                     _ <- reset.when(preview.runOption != RunOption.RemainOpen)
                   } yield ()
                 }
          } yield ()
      }
  })

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

  def clearScreen: UIO[Unit] =
    ZIO.succeed {
      document.remove(0, document.getLength)
    }

  def open: Task[Unit] =
    ZIO.attempt {
      val bounds =
        GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice.getDefaultConfiguration.getBounds

      val x = (bounds.width - frame.getWidth) / 2

      frame.setLocation(x, 0)
      frame.setVisible(true)

    }

  def hide: URIO[Conf, Unit] =
    for {
      keepOpen <- Conf.get(_.general.keepOpen)
      _ <- if (keepOpen)
             WindowManager.switchFocusToPreviousActiveWindow.when(keepOpen).ignore
           else
             ZIO.succeed(frame.setVisible(false))
    } yield ()

  def activate: RIO[Tools, Unit] =
    OS.os match {
      case OS.MacOS => Tools.activate
      case _ =>
        ZIO.succeed {
          frame.toFront()
          frame.requestFocus()
          inputTextField.requestFocusInWindow()
        }
    }

  def deactivate: RIO[Tools, Unit] =
    OS.os match {
      case OS.MacOS => Tools.hide
      case _        => ZIO.unit
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
      _ <- ZIO.attempt {
             inputTextField.setFont(preferredFont)
             outputTextPane.setFont(preferredFont)
           }
    } yield ()

  def getPreferredFont: URIO[Conf, Font] = {
    def fallbackFont = new Font("Monospaced", Font.PLAIN, 18)

    (for {
      fonts <- Conf.get(_.display.fonts)
      installedFontNames <-
        ZIO.attempt(
          GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames.map(_.toLowerCase).toSet
        )
      font = fonts.find(f => installedFontNames.contains(f.getName.toLowerCase)).getOrElse(fallbackFont)
    } yield font).orElse(ZIO.succeed(fallbackFont))
  }

  def getPreferredFrameWidth: URIO[Conf, Int] =
    for {
      width <- Conf.get(_.display.width)
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
      runtime          <- ZIO.runtime[Env]
      debounceDelay    <- Conf.get(_.general.debounceDelay)
      searchDebouncer  <- Debouncer.make[Env, Nothing, Unit](debounceDelay, Some(10.seconds)) // TODO:: Add config
      commandCursorRef <- Ref.make(0)
      searchResultsRef <- Ref.make(SearchResults.empty[Any])
      closePromise     <- Promise.make[Nothing, Unit]
      swingTerminal <-
        ZIO.acquireRelease(
          ZIO.succeed(new SwingTerminal(commandCursorRef, searchResultsRef, searchDebouncer, closePromise)(runtime))
        )(t => ZIO.succeed(t.frame.dispose()))
      _ <- swingTerminal.init
    } yield swingTerminal
}

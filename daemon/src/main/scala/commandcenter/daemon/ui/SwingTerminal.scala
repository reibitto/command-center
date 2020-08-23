package commandcenter.daemon.ui

import java.awt.event.KeyEvent
import java.awt.{ BorderLayout, Color, Dimension, Font, GraphicsEnvironment, KeyboardFocusManager }

import commandcenter.CCRuntime.Env
import commandcenter._
import commandcenter.command.{ Command, CommandResult, PreviewResult, SearchResults }
import commandcenter.daemon.event.KeyboardShortcutUtil
import commandcenter.locale.Language
import commandcenter.tools.Tools
import commandcenter.ui.CCTheme
import commandcenter.util.{ Debounced, GraphicsUtil, OS }
import commandcenter.view.AnsiRendered
import javax.swing._
import javax.swing.plaf.basic.BasicScrollBarUI
import javax.swing.text.{ DefaultStyledDocument, StyleConstants, StyleContext }
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._

import scala.annotation.tailrec

final case class SwingTerminal(
  var config: CCConfig, // TODO: Convert to Ref
  commandCursorRef: Ref[Int],
  searchResultsRef: Ref[SearchResults[Any]],
  searchDebounce: URIO[Env, Unit] => URIO[Env with Clock, Fiber[Nothing, Unit]]
)(implicit runtime: Runtime[Env])
    extends CCTerminal {
  val terminalType: TerminalType = TerminalType.Swing

  val theme    = CCTheme.default
  val document = new DefaultStyledDocument
  val context  = new StyleContext
  val frame    = new JFrame("Command Center")
  val font     = filterMissingFonts(config.display.fonts).headOption.getOrElse(new Font("Monospaced", Font.PLAIN, 18))

  val preferredFrameWidth: Int =
    config.display.width min GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice.getDefaultConfiguration.getBounds.width

  frame.setBackground(theme.background)
  frame.setFocusable(false)
  frame.setUndecorated(true)
  if (runtime.unsafeRun(GraphicsUtil.isOpacitySupported))
    frame.setOpacity(config.display.opacity)
  frame.getContentPane.setLayout(new BorderLayout())

  val inputTextField = new ZTextField
  inputTextField.setFont(font)
  inputTextField.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10))
  inputTextField.setBackground(theme.background)
  inputTextField.setForeground(theme.foreground)
  inputTextField.setCaretColor(theme.foreground)
  // Enable ability to detect Tab key presses
  inputTextField.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, java.util.Collections.emptySet())
  frame.getContentPane.add(inputTextField, BorderLayout.NORTH)

  val outputTextPane = new JTextPane(document)
  outputTextPane.setFocusable(false)
  outputTextPane.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 10))
  outputTextPane.setFont(font)
  outputTextPane.setBackground(theme.background)
  outputTextPane.setForeground(theme.foreground)
//  outputTextPane.setCaretColor(Color.RED) // TODO: Make caret color configurable
  outputTextPane.setEditable(false)

  val outputScrollPane = new JScrollPane(
    outputTextPane,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  ) {
    override def getPreferredSize: Dimension = {
      val height =
        if (runtime.unsafeRun(searchResultsRef.get).previews.isEmpty)
          0
        else
          outputTextPane.getPreferredSize.height min config.display.maxHeight

      new Dimension(preferredFrameWidth, height)
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
    val context    = CommandContext(Language.detect(searchTerm), SwingTerminal.this, 1.0)

    searchDebounce(
      Command
        .search(config.commands, config.aliases, searchTerm, context)
        .tap(r => commandCursorRef.set(0) *> searchResultsRef.set(r) *> render(r))
        .unit
    ).flatMap(_.join)
  }

  private def render(searchResults: SearchResults[Any]): UIO[Unit] =
    for {
      commandCursor <- commandCursorRef.get
    } yield SwingUtilities.invokeLater { () =>
      def colorMask(width: Int): Long = ~0L >>> (64 - width)

      document.remove(0, document.getLength)

      var scrollToPosition: Int = 0

      val str = searchResults.rendered.zipWithIndex.map {
        case (r, i) =>
          r match {
            case ar: AnsiRendered =>
              val bar =
                if (i == commandCursor)
                  fansi.Back.Green(" ")
                else
                  fansi.Back.DarkGray(" ")

              if (i < commandCursor)
                scrollToPosition += ar.ansiStr.length + 3

              bar ++ fansi.Str(" ") ++ ar.ansiStr
          }
      }.reduceOption(_ ++ fansi.Str("\n") ++ _).getOrElse(fansi.Str(""))

      var i: Int = 0
      groupConsecutive(str.getColors.toList).foreach { c =>
        val s = str.plainText.substring(i, i + c.length)

        i += c.length

        val ansiForeground = (c.head >>> fansi.Color.offset) & colorMask(fansi.Color.width)
        val ansiBackground = (c.head >>> fansi.Back.offset) & colorMask(fansi.Back.width)

        val awtForeground = CCTheme.default.fromFansiColorCode(ansiForeground.toInt)
        val awtBackground = CCTheme.default.fromFansiColorCode(ansiBackground.toInt)

        val style = context.addStyle(ansiForeground.toString, null)
        awtForeground.foreach(StyleConstants.setForeground(style, _))
        awtBackground.foreach(StyleConstants.setBackground(style, _))

        document.insertString(document.getLength, s, style)
      }

      outputTextPane.setCaretPosition(scrollToPosition)

      frame.pack()
    }

  def reset(): UIO[Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- UIO(inputTextField.setText(""))
      _ <- UIO(document.remove(0, document.getLength))
      _ <- searchResultsRef.set(SearchResults.empty)
    } yield ()

  def runSelected(results: SearchResults[Any], cursorIndex: Int): RIO[Env, Option[PreviewResult[Any]]] = {
    val previewResult = results.previews.lift(cursorIndex)

    for {
      _ <- ZIO.foreach(previewResult) { preview =>
             // TODO: Log defects
             preview.onRun.absorb.forkDaemon
           }
      _ <- reset()
    } yield previewResult
  }

  inputTextField.addZKeyListener(new ZKeyAdapter {
    override def keyPressed(e: KeyEvent): URIO[Env, Unit] =
      e.getKeyCode match {
        case KeyEvent.VK_ENTER  =>
          for {
            _               <- UIO(frame.setVisible(false))
            previousResults <- searchResultsRef.get
            cursorIndex     <- commandCursorRef.get
            resultOpt       <- runSelected(previousResults, cursorIndex).catchAll(_ => UIO.none)
            _               <- ZIO.whenCase(resultOpt) {
                                 case Some(o) if o.result == CommandResult.Exit =>
                                   UIO(System.exit(0))
                               }
          } yield ()

        case KeyEvent.VK_ESCAPE =>
          for {
            _ <- UIO(frame.setVisible(false))
            _ <- deactivate.ignore
            _ <- reset()
          } yield ()

        case KeyEvent.VK_DOWN   =>
          for {
            _               <- UIO(e.consume())
            previousResults <- searchResultsRef.get
            _               <- commandCursorRef.update(cursor => (cursor + 1) min (previousResults.previews.length - 1))
            _               <- render(previousResults)
          } yield ()

        case KeyEvent.VK_UP     =>
          for {
            _               <- UIO(e.consume())
            previousResults <- searchResultsRef.get
            _               <- commandCursorRef.update(cursor => (cursor - 1) max 0)
            _               <- render(previousResults)
          } yield ()

        case _                  =>
          for {
            previousResults <- searchResultsRef.get
            shortcutPressed  = KeyboardShortcutUtil.fromKeyEvent(e)
            eligibleResults  = previousResults.previews.filter { p =>
                                 p.source.shortcuts.contains(shortcutPressed)
                               }
            bestMatch        = eligibleResults.maxByOption(_.score)
            _               <- ZIO.foreach(bestMatch) { preview =>
                                 for {
                                   _ <- UIO(frame.setVisible(false))
                                   _ <- preview.onRun.absorb.forkDaemon // TODO: Log defects
                                   _ <- reset()
                                 } yield ()
                               }
          } yield ()
      }
  })

  frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
  frame.setMinimumSize(new Dimension(preferredFrameWidth, 20))
  frame.pack()

  def clearScreen: UIO[Unit] =
    UIO {
      document.remove(0, document.getLength)
    }

  def open: Task[Unit] =
    Task {
      val bounds =
        GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice.getDefaultConfiguration.getBounds

      val x = (bounds.width - frame.getWidth) / 2

      frame.setLocation(x, 0)
      frame.setVisible(true)
    }

  def hide: UIO[Unit] = UIO(frame.setVisible(false))

  def activate: RIO[Tools with Blocking, Unit] =
    OS.os match {
      case OS.MacOS => tools.activate
      case _        =>
        UIO {
          frame.toFront()
          frame.requestFocus()
          inputTextField.requestFocusInWindow()
        }
    }

  def deactivate: RIO[Tools with Blocking, Unit] =
    OS.os match {
      case OS.MacOS => tools.hide
      case _        => UIO.unit
    }

  def opacity: RIO[Env, Float] = UIO(frame.getOpacity)

  def setOpacity(opacity: Float): RIO[Env, Unit] = Task(frame.setOpacity(opacity))

  def size: RIO[Env, Dimension] = UIO(frame.getSize)

  def setSize(width: Int, maxHeight: Int): RIO[Env, Unit] =
    UIO {
      config = config.copy(
        display = config.display.copy(width = width, maxHeight = maxHeight)
      )
    }

  def reload: RIO[Env, Unit] =
    CCConfig.load.use { newConfig =>
      UIO {
        config = newConfig
      }
    }

  private def filterMissingFonts(fonts: List[Font]): List[Font] = {
    val installedFontNames = GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames.toSet

    fonts.filter(f => installedFontNames.contains(f.getName))
  }

  @tailrec
  private def groupConsecutive[A](list: List[A], acc: List[List[A]] = Nil): List[List[A]] =
    list match {
      case head :: tail =>
        val (t1, t2) = tail.span(_ == head)
        groupConsecutive(t2, acc :+ (head :: t1))
      case _            => acc
    }
}

object SwingTerminal {
  def create(config: CCConfig, runtime: CCRuntime): Managed[Throwable, SwingTerminal] =
    for {
      searchDebounce   <- Debounced[Env, Nothing, Unit](250.millis).toManaged_
      commandCursorRef <- Ref.makeManaged(0)
      searchResultsRef <- Ref.makeManaged(SearchResults.empty[Any])
    } yield new SwingTerminal(config, commandCursorRef, searchResultsRef, searchDebounce)(runtime)
}

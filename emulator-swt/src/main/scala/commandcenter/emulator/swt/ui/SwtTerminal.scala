package commandcenter.emulator.swt.ui

import commandcenter.*
import commandcenter.command.*
import commandcenter.emulator.swt.event.KeyEventExtensions.KeyEventExtension
import commandcenter.emulator.swt.event.KeyboardShortcutUtil
import commandcenter.emulator.util.AnsiRun
import commandcenter.ui.SearchDebouncer
import commandcenter.util.{Debouncer, WindowManager}
import commandcenter.util.RuntimeExtensions.*
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.{KeyAdapter, KeyEvent, ModifyEvent, ModifyListener}
import org.eclipse.swt.SWT
import zio.*

import java.awt.Dimension
import scala.collection.mutable
import scala.util.Try

final case class SwtTerminal(
    terminal: RawSwtTerminal,
    searchDebouncer: Debouncer[Env, Nothing, Unit],
    commandCursorRef: Ref[Int],
    searchResultsRef: Ref[SearchResults[Any]],
    consecutiveOpenCountRef: Ref[Int]
)(implicit runtime: Runtime[Env])
    extends BaseGuiTerminal {
  val terminalType: TerminalType = TerminalType.Swt

  val smartBuffer: TerminalBuffer = new TerminalBuffer(new StringBuilder, mutable.ArrayDeque.empty)

  terminal.display.asyncExec { () =>
    terminal.inputBox.addModifyListener(new ModifyListener {

      override def modifyText(e: ModifyEvent): Unit = {
        val searchTerm = terminal.inputBox.getText
        runtime.unsafeRun(ZIO.logTrace(s"Input text was modified to `$searchTerm`") *> onInputChanged(searchTerm))
      }
    })

    terminal.outputBox.addKeyListener(new KeyAdapter {
      override def keyPressed(e: KeyEvent): Unit =
        e.keyCode match {
          case SWT.ESC =>
            fork {
              invoke {
                terminal.inputBox.setSelection(terminal.inputBox.getText.length)
                terminal.inputBox.setFocus()
              }
            }

          case _ =>
            val shortcutPressed = KeyboardShortcutUtil.fromKeyEvent(e)

            fork {
              invoke {
                val text = terminal.outputBox.getSelectionText
                terminal.inputBox.setText(text)
                terminal.inputBox.setSelection(text.length)
                terminal.inputBox.setFocus()
              }.whenZIO(Conf.get(_.keyboard.outputLookupShortcut == shortcutPressed))
            }
        }
    })

    terminal.inputBox.addKeyListener(new KeyAdapter {

      override def keyReleased(e: KeyEvent): Unit =
        e.keyCode match {
          case SWT.CR | SWT.KEYPAD_CR => fork(onRelease(runSelected))
          case SWT.ESC                => fork(onRelease(resetAndHide))
          case _                      => ()
        }

      override def keyPressed(e: KeyEvent): Unit =
        e.keyCode match {
          // We also check the `character` field to that hitting `Enter` in IME composition mode doesn't immediately
          // run the command.
          case SWT.CR if e.character == SWT.CR => fork(onPress(runSelected))
          case SWT.KEYPAD_CR                   => fork(onPress(runSelected))
          case SWT.ESC                         => fork(onPress(resetAndHide))
          case SWT.ARROW_DOWN if e.isAltDown   => fork(invoke(terminal.outputBox.setFocus()))

          case SWT.ARROW_DOWN =>
            e.doit = false // Must be set synchronously in the SWT callback, so it can't be part of the effect

            fork {
              for {
                previousResults <- searchResultsRef.get
                previousCursor  <-
                  commandCursorRef.getAndUpdate(cursor => (cursor + 1) min (previousResults.previews.length - 1))
                _ <- renderSelectionCursor(-1).when(previousCursor < previousResults.previews.length - 1)
              } yield ()
            }

          case SWT.ARROW_UP =>
            e.doit = false // Must be set synchronously in the SWT callback, so it can't be part of the effect

            fork {
              for {
                previousCursor <- commandCursorRef.getAndUpdate(cursor => (cursor - 1) max 0)
                _              <- renderSelectionCursor(1).when(previousCursor > 0)
              } yield ()
            }

          case _ =>
            val shortcutPressed = KeyboardShortcutUtil.fromKeyEvent(e)
            fork(runShortcut(shortcutPressed))
        }
    })
  }

  private def fork(effect: URIO[Env, Any]): Unit = runtime.unsafeFork(effect)

  private def renderSelectionCursor(cursorDelta: Int): UIO[Unit] =
    for {
      commandCursor <- commandCursorRef.get
      textIndex = smartBuffer.lineStartIndices(commandCursor)
      priorTextIndex = smartBuffer.lineStartIndices(commandCursor + cursorDelta)
      _ <- invoke {
             terminal.outputBox.replaceStyleRanges(
               0,
               0,
               Array(
                 new StyleRange(
                   priorTextIndex,
                   1,
                   null,
                   terminal.darkGray
                 ),
                 new StyleRange(
                   textIndex,
                   1,
                   null,
                   terminal.green
                 )
               ).sortBy(_.start)
             )

             terminal.outputBox.setSelection(textIndex)
           }
    } yield ()

  override protected def render(searchResults: SearchResults[Any]): URIO[Env, Unit] =
    for {
      commandCursor              <- commandCursorRef.get
      (styles, scrollToPosition) <- ZIO.succeed(fillBuffer(searchResults, commandCursor))
      config                     <- Conf.config
      _                          <- invoke {
             if (smartBuffer.buffer.isEmpty || !terminal.shell.isVisible) {
               terminal.outputBox.setVisible(false)
               terminal.outputBoxGridData.exclude = true
               terminal.outputBox.setText("")
             } else {
               terminal.outputBox.setVisible(true)
               terminal.outputBoxGridData.exclude = false
               terminal.outputBox.setText(smartBuffer.buffer.toString)

               terminal.outputBox.setStyleRanges(styles)
             }

             val newSize = terminal.shell.computeSize(config.display.width, SWT.DEFAULT)

             // The scrollbar displays as scrollable even if it's the exact size, so add one more pixel to prevent that.
             val newHeight = newSize.y + 1

             terminal.shell.setSize(config.display.width, newHeight min config.display.maxHeight)

             terminal.outputBox.setSelection(scrollToPosition)
           }
    } yield ()

  // Rewrites `smartBuffer` with the rendered results and returns the style ranges and the cursor's scroll position.
  private def fillBuffer(searchResults: SearchResults[Any], commandCursor: Int): (Array[StyleRange], Int) = {
    smartBuffer.clear()

    val styles = mutable.ArrayBuffer.empty[StyleRange]
    var scrollToPosition = 0
    var lineStart = 0

    searchResults.rendered.zipWithIndex.foreach {
      case (Rendered.Styled(_), _) => ()

      case (ar: Rendered.Ansi, row) =>
        val barColor = if (row == commandCursor) terminal.green else terminal.darkGray
        styles.append(new StyleRange(smartBuffer.buffer.length, 1, terminal.black, barColor))
        smartBuffer.buffer.append("  ")

        if (row < commandCursor)
          scrollToPosition += ar.ansiStr.length + 3

        val renderStr = if (row < searchResults.rendered.length - 1) ar.ansiStr ++ "\n" else ar.ansiStr

        smartBuffer.lineStartIndices.addOne(lineStart)
        lineStart += renderStr.length + 2

        AnsiRun.runs(renderStr).foreach { run =>
          val start = smartBuffer.buffer.length
          smartBuffer.buffer.append(run.text)

          val foreground = terminal.fromFansiColorCode(run.foreground)
          val background = terminal.fromFansiColorCode(run.background)

          // Don't bother wastefully creating a StyleRange object
          if (foreground.nonEmpty || background.nonEmpty)
            styles.append(new StyleRange(start, run.text.length, foreground.orNull, background.orNull))
        }
    }

    (styles.toArray, scrollToPosition)
  }

  def invoke(effect: => Unit): UIO[Unit] =
    ZIO.succeed(terminal.display.asyncExec(() => effect))

  // Evaluated on the UI thread. Exceptions are passed to the callback so the fiber can't hang.
  def invokeReturn[A](effect: => A): Task[A] =
    ZIO.async { cb =>
      terminal.display.asyncExec(() => cb(Exit.fromTry(Try(effect))))
    }

  def reset: URIO[Env, Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- invoke {
             terminal.inputBox.setText("")
           }
      _ <- searchResultsRef.set(SearchResults.empty)
    } yield ()

  def openActivated: RIO[Env, Unit] =
    for {
      consecutiveOpenCount <- consecutiveOpenCountRef.updateAndGet(_ + 1)
      config               <- Conf.config
      _                    <- openAndActivate(config.general.reopenDelay)
      _                    <- ZIO.foreachDiscard(config.display.alternateOpacity) { alternateOpacity =>
             setOpacity(if (consecutiveOpenCount % 2 == 0) alternateOpacity else config.display.opacity).ignore
           }
    } yield ()

  def open: URIO[Env, Unit] =
    for {
      conf <- Conf.config
      _    <- invoke {
             val bounds = terminal.display.getPrimaryMonitor.getClientArea
             val x = (bounds.width - terminal.shell.getSize.x) / 2
             terminal.shell.setLocation(x + conf.display.offsetX, conf.display.offsetY)
             terminal.shell.open()
           }
    } yield ()

  def hide: URIO[Conf, Unit] =
    for {
      keepOpen <- Conf.get(_.general.keepOpen)
      _        <- if (keepOpen)
             WindowManager.switchFocusToPreviousActiveWindow.ignore
           else
             invoke(terminal.shell.setVisible(false))
      _ <- consecutiveOpenCountRef.set(0)
    } yield ()

  def activate: UIO[Unit] =
    invoke(terminal.shell.forceActive())

  def opacity: RIO[Env, Float] = invokeReturn(terminal.shell.getAlpha / 255.0f)

  def setOpacity(opacity: Float): RIO[Env, Unit] = invoke(terminal.shell.setAlpha((255 * opacity).toInt))

  def isOpacitySupported: URIO[Env, Boolean] = ZIO.succeed(true)

  def size: RIO[Env, Dimension] =
    invokeReturn {
      val size = terminal.shell.getSize
      new Dimension(size.x, size.y)
    }

  def setSize(width: Int, height: Int): RIO[Env, Unit] = invoke(terminal.shell.setSize(width, height))

  def reload: RIO[Env, Unit] =
    for {
      config <- Conf.reload
      _      <- setOpacity(config.display.opacity)
      _      <- invoke {
             val previousFont = terminal.inputBox.getFont
             val preferredFont = terminal.getPreferredFont(config.display.fonts)
             terminal.inputBox.setFont(preferredFont)
             terminal.outputBox.setFont(preferredFont)
             // SWT fonts are native resources and must be disposed explicitly.
             if (previousFont != preferredFont) previousFont.dispose()
           }
    } yield ()
}

object SwtTerminal {

  def create(runtime: Runtime[Env], terminal: RawSwtTerminal): RIO[Scope & Env, SwtTerminal] =
    for {
      searchDebouncer         <- SearchDebouncer.make
      commandCursorRef        <- Ref.make(0)
      searchResultsRef        <- Ref.make(SearchResults.empty[Any])
      consecutiveOpenCountRef <- Ref.make(0)
      swtTerminal = SwtTerminal(terminal, searchDebouncer, commandCursorRef, searchResultsRef, consecutiveOpenCountRef)(
                      runtime
                    )
      _ <- swtTerminal.init
    } yield swtTerminal
}

package commandcenter.emulator.swt.ui

import commandcenter.*
import commandcenter.command.*
import commandcenter.emulator.swt.event.KeyEventExtensions.KeyEventExtension
import commandcenter.emulator.swt.event.KeyboardShortcutUtil
import commandcenter.emulator.util.Lists
import commandcenter.locale.Language
import commandcenter.util.{Debouncer, WindowManager}
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.{KeyAdapter, KeyEvent, ModifyEvent, ModifyListener}
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.SWT
import zio.*

import java.awt.Dimension
import scala.collection.mutable

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

  def init: RIO[Env, Unit] =
    for {
      opacity <- Conf.get(_.display.opacity)
      _       <- setOpacity(opacity)
    } yield ()

  Display.getDefault.asyncExec { () =>
    terminal.inputBox.addModifyListener(new ModifyListener {

      override def modifyText(e: ModifyEvent): Unit = {
        val searchTerm = terminal.inputBox.getText

        Unsafe.unsafe { implicit u =>
          runtime.unsafe.run {
            for {
              _             <- ZIO.logTrace(s"Input text was modified to `$searchTerm`")
              context       <- ZIO.succeed(CommandContext(Language.detect(searchTerm), SwtTerminal.this, 1.0))
              config        <- Conf.config
              searchResults <- searchResultsRef.get
              // This is an optimization but it also helps with certain IMEs where they resend all the changes when
              // exiting out of composition mode (committing the changes).
              sameAsLast = searchResults.searchTerm == searchTerm && searchTerm.nonEmpty
              _ <- searchDebouncer(
                     Command
                       .search(config.commands, config.aliases, searchTerm, context)
                       .tap(r => setResults(r))
                       .unless(sameAsLast)
                       .unit
                   )
            } yield ()
          }
        }
      }
    })

    terminal.outputBox.addKeyListener(new KeyAdapter {
      override def keyPressed(e: KeyEvent): Unit =
        e.keyCode match {
          case SWT.ESC =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
                invoke {
                  terminal.inputBox.setSelection(terminal.inputBox.getText.length)
                  terminal.inputBox.setFocus()
                }
              }
            }

          case _ =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
                for {
                  config <- Conf.config
                  shortcutPressed = KeyboardShortcutUtil.fromKeyEvent(e)
                  _ <- invoke {
                         val text = terminal.outputBox.getSelectionText
                         terminal.inputBox.setText(text)
                         terminal.inputBox.setSelection(text.length)
                         terminal.inputBox.setFocus()
                       }.when(config.keyboard.outputLookupShortcut == shortcutPressed)
                } yield ()
              }
            }

        }
    })

    terminal.inputBox.addKeyListener(new KeyAdapter {

      override def keyReleased(e: KeyEvent): Unit =
        e.keyCode match {
          case SWT.CR | SWT.KEYPAD_CR =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
                runSelected.whenZIO(Conf.get(_.general.hideOnKeyRelease))
              }
            }

          case SWT.ESC =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
                resetAndHide.whenZIO(Conf.get(_.general.hideOnKeyRelease))
              }
            }

          case _ => ()
        }

      override def keyPressed(e: KeyEvent): Unit =
        e.keyCode match {
          case SWT.CR | SWT.KEYPAD_CR =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
                runSelected.unlessZIO(Conf.get(_.general.hideOnKeyRelease))
              }
            }

          case SWT.ESC =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
                resetAndHide.unlessZIO(Conf.get(_.general.hideOnKeyRelease))
              }
            }

          case SWT.ARROW_DOWN if e.isAltDown =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
                invoke {
                  terminal.outputBox.setFocus()
                }
              }
            }

          case SWT.ARROW_DOWN =>
            e.doit = false // Not ideal to have it outside the for-comprehension, but wrapping this in UIO will not work

            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
                for {
                  previousResults <- searchResultsRef.get
                  previousCursor  <-
                    commandCursorRef.getAndUpdate(cursor => (cursor + 1) min (previousResults.previews.length - 1))
                  _ <- renderSelectionCursor(-1).when(previousCursor < previousResults.previews.length - 1)
                } yield ()
              }
            }

          case SWT.ARROW_UP =>
            e.doit = false // Not ideal to have it outside the for-comprehension, but wrapping this in UIO will not work

            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
                for {
                  previousCursor <- commandCursorRef.getAndUpdate(cursor => (cursor - 1) max 0)
                  _              <- renderSelectionCursor(1).when(previousCursor > 0)
                } yield ()
              }
            }

          case _ =>
            Unsafe.unsafe { implicit u =>
              runtime.unsafe.fork {
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
            }
        }
    })
  }

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

  override protected def render(searchResults: SearchResults[Any]): URIO[Env, Unit] = {
    var scrollToPosition: Int = 0

    for {
      commandCursor <- commandCursorRef.get
      _ = smartBuffer.clear()
      styles = new mutable.ArrayDeque[StyleRange]()
      _ = {
        def renderBar(rowIndex: Int): Unit = {
          val barColor = if (rowIndex == commandCursor) terminal.green else terminal.darkGray

          styles.append(new StyleRange(smartBuffer.buffer.length, 1, terminal.black, barColor))
          smartBuffer.buffer.append("  ")
        }

        def colorMask(width: Int): Long = ~0L >>> (64 - width)

        var lineStart = 0

        searchResults.rendered.zipWithIndex.foreach { case (r, row) =>
          r match {
            case Rendered.Styled(segments) => ()

            case ar: Rendered.Ansi =>
              renderBar(row)

              if (row < commandCursor)
                scrollToPosition += ar.ansiStr.length + 3

              val renderStr = if (row < searchResults.rendered.length - 1) ar.ansiStr ++ "\n" else ar.ansiStr

              smartBuffer.lineStartIndices.addOne(lineStart)
              lineStart += renderStr.length + 2

              var i: Int = 0
              Lists.groupConsecutive(renderStr.getColors.toList).foreach { c =>
                val s = renderStr.plainText.substring(i, i + c.length)

                i += c.length

                val ansiForeground = (c.head >>> fansi.Color.offset) & colorMask(fansi.Color.width)
                val ansiBackground = (c.head >>> fansi.Back.offset) & colorMask(fansi.Back.width)

                smartBuffer.buffer.append(s)

                val swtForegroundOpt = terminal.fromFansiColorCode(ansiForeground.toInt)
                val swtBackgroundOpt = terminal.fromFansiColorCode(ansiBackground.toInt)

                (swtForegroundOpt, swtBackgroundOpt) match {
                  case (None, None) =>
                  // Don't bother wastefully creating a StyleRange object

                  case _ =>
                    styles.append(
                      new StyleRange(
                        smartBuffer.buffer.length - s.length,
                        s.length,
                        swtForegroundOpt.orNull,
                        swtBackgroundOpt.orNull
                      )
                    )
                }
              }
          }
        }
      }

      config <- Conf.config
      _      <- invoke {
             if (smartBuffer.buffer.isEmpty || !terminal.shell.isVisible) {
               terminal.outputBox.setVisible(false)
               terminal.outputBoxGridData.exclude = true
               terminal.outputBox.setText("")
             } else {
               terminal.outputBox.setVisible(true)
               terminal.outputBoxGridData.exclude = false
               terminal.outputBox.setText(smartBuffer.buffer.toString)

               terminal.outputBox.setStyleRanges(styles.toArray)
             }

             val newSize = terminal.shell.computeSize(config.display.width, SWT.DEFAULT)

             // The scrollbar displays as scrollable even if it's the exact size, so add one more pixel to prevent that.
             val newHeight = newSize.y + 1

             terminal.shell.setSize(config.display.width, newHeight min config.display.maxHeight)

             terminal.outputBox.setSelection(scrollToPosition)
           }
    } yield ()
  }

  def invoke(effect: => Unit): UIO[Unit] =
    ZIO.succeed(Display.getDefault.asyncExec(() => effect))

  def invokeReturn[A](effect: => A): Task[A] =
    ZIO.asyncZIO { cb =>
      invoke {
        val evaluatedEffect = effect
        cb(ZIO.succeed(evaluatedEffect))
      }
    }

  def reset: URIO[Env, Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- invoke {
             terminal.inputBox.setText("")
           }
      _ <- searchResultsRef.set(SearchResults.empty)
    } yield ()

  def openActivated: URIO[Env, Unit] =
    for {
      consecutiveOpenCount <- consecutiveOpenCountRef.updateAndGet(_ + 1)
      config               <- Conf.config
      _                    <- open
      _                    <- activate
      _                    <- ZIO.foreachDiscard(config.general.reopenDelay) { delay =>
             for {
               _ <- ZIO.sleep(delay)
               _ <- open
               _ <- activate
             } yield ()
           }
      _ <- ZIO.foreachDiscard(config.display.alternateOpacity) { alternateOpacity =>
             if (consecutiveOpenCount % 2 == 0)
               setOpacity(alternateOpacity).ignore
             else
               setOpacity(config.display.opacity).ignore
           }
    } yield ()

  def open: URIO[Env, Unit] =
    for {
      _ <- invoke {
             val bounds = terminal.shell.getDisplay.getPrimaryMonitor.getClientArea
             val x = (bounds.width - terminal.shell.getSize.x) / 2
             terminal.shell.setLocation(x, 0)
             terminal.shell.open()
           }
    } yield ()

  def hide: URIO[Conf, Unit] =
    for {
      keepOpen <- Conf.get(_.general.keepOpen)
      _        <- if (keepOpen)
             WindowManager.switchFocusToPreviousActiveWindow.when(keepOpen).ignore
           else
             invoke(terminal.shell.setVisible(false))
      _ <- consecutiveOpenCountRef.set(0)
    } yield ()

  def activate: UIO[Unit] =
    invoke(terminal.shell.forceActive())

  def opacity: RIO[Env, Float] = invokeReturn(terminal.shell.getAlpha / 255.0f)

  def setOpacity(opacity: Float): RIO[Env, Unit] = invoke {
    terminal.shell.setAlpha((255 * opacity).toInt)
  }.whenZIO(isOpacitySupported).unit

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
             val preferredFont = terminal.getPreferredFont(config.display.fonts)
             terminal.inputBox.setFont(preferredFont)
             terminal.outputBox.setFont(preferredFont)
           }
    } yield ()
}

object SwtTerminal {

  def create(runtime: Runtime[Env], terminal: RawSwtTerminal): RIO[Scope & Env, SwtTerminal] =
    for {
      generalConfig   <- Conf.get(_.general)
      searchDebouncer <- Debouncer.make[Env, Nothing, Unit](
                           generalConfig.debounceDelay,
                           Some(generalConfig.opTimeoutOrDefault)
                         )
      commandCursorRef        <- Ref.make(0)
      searchResultsRef        <- Ref.make(SearchResults.empty[Any])
      consecutiveOpenCountRef <- Ref.make(0)
      swtTerminal = new SwtTerminal(
                      terminal,
                      searchDebouncer,
                      commandCursorRef,
                      searchResultsRef,
                      consecutiveOpenCountRef
                    )(runtime)
      _ <- swtTerminal.init
    } yield swtTerminal
}

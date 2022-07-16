package commandcenter.emulator.swt.ui

import commandcenter.*
import commandcenter.command.*
import commandcenter.emulator.swt.event.KeyboardShortcutUtil
import commandcenter.emulator.util.Lists
import commandcenter.locale.Language
import commandcenter.tools.Tools
import commandcenter.ui.CCTheme
import commandcenter.util.{Debouncer, OS}
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.{KeyAdapter, KeyEvent, ModifyEvent, ModifyListener}
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.SWT
import zio.*
import zio.blocking.Blocking
import zio.stream.ZSink

import java.awt.Dimension
import scala.collection.mutable

final case class SwtTerminal(
    commandCursorRef: Ref[Int],
    searchResultsRef: Ref[SearchResults[Any]],
    searchDebouncer: Debouncer[Env, Nothing, Unit],
    terminal: RawSwtTerminal
)(implicit runtime: Runtime[Env])
    extends GuiTerminal {
  val terminalType: TerminalType = TerminalType.Swt

  val theme = CCTheme.default

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
        val context = CommandContext(Language.detect(searchTerm), SwtTerminal.this, 1.0)

        runtime.unsafeRunAsync_ {
          for {
            config <- Conf.config
            _ <- searchDebouncer(
                   Command
                     .search(config.commands, config.aliases, searchTerm, context)
                     .tap(r => commandCursorRef.set(0) *> searchResultsRef.set(r) *> render(r))
                     .unit
                 ).flatMap(_.join)
          } yield ()
        }
      }
    })

    terminal.inputBox.addKeyListener(new KeyAdapter {
      override def keyPressed(e: KeyEvent): Unit =
        e.keyCode match {
          case SWT.CR =>
            runtime.unsafeRunAsync_ {
              for {
                _               <- searchDebouncer.triggerNowAwait
                previousResults <- searchResultsRef.get
                cursorIndex     <- commandCursorRef.get
                resultOpt       <- runSelected(previousResults, cursorIndex).catchAll(_ => UIO.none)
                _ <- ZIO.whenCase(resultOpt.map(_.runOption)) { case Some(RunOption.Exit) =>
                       invoke(terminal.shell.dispose())
                     }
              } yield ()
            }

          case SWT.ESC =>
            runtime.unsafeRunAsync_ {
              for {
                _ <- hide
                _ <- deactivate.ignore
                _ <- reset
              } yield ()
            }

          case SWT.ARROW_DOWN =>
            e.doit = false // Not ideal to have it outside the for-comprehension, but wrapping this in UIO will not work

            runtime.unsafeRunAsync_ {
              for {
                previousResults <- searchResultsRef.get
                previousCursor <-
                  commandCursorRef.getAndUpdate(cursor => (cursor + 1) min (previousResults.previews.length - 1))
                _ <- renderSelectionCursor(-1).when(previousCursor < previousResults.previews.length - 1)
              } yield ()
            }

          case SWT.ARROW_UP =>
            e.doit = false // Not ideal to have it outside the for-comprehension, but wrapping this in UIO will not work

            runtime.unsafeRunAsync_ {
              for {
                previousCursor <- commandCursorRef.getAndUpdate(cursor => (cursor - 1) max 0)
                _              <- renderSelectionCursor(1).when(previousCursor > 0)
              } yield ()
            }

          case _ =>
            runtime.unsafeRunAsync_ {
              for {
                previousResults <- searchResultsRef.get
                shortcutPressed = KeyboardShortcutUtil.fromKeyEvent(e)
                eligibleResults = previousResults.previews.filter { p =>
                                    p.shortcuts.contains(shortcutPressed)
                                  }
                bestMatch = eligibleResults.maxByOption(_.score)
                _ <- ZIO.foreach_(bestMatch) { preview =>
                       for {
                         _ <- hide
                         _ <- preview.onRun.absorb.forkDaemon // TODO: Log defects
                         _ <- reset
                       } yield ()
                     }
              } yield ()
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

  private def render(searchResults: SearchResults[Any]): URIO[Has[Conf], Unit] = {
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
      _ <- invoke {
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
             terminal.shell.setSize(config.display.width, newSize.y min config.display.maxHeight)

             terminal.outputBox.setSelection(scrollToPosition)
           }
    } yield ()
  }

  def runSelected(results: SearchResults[Any], cursorIndex: Int): RIO[Env, Option[PreviewResult[Any]]] =
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
                 case MoreResults.Remaining(p @ PreviewResults.Paginated(rs, pageSize, totalRemaining))
                     if totalRemaining.forall(_ > 0) =>
                   for {
                     _                     <- preview.onRun.absorb.forkDaemon
                     (results, restStream) <- rs.peel(ZSink.take(pageSize)).useNow.mapError(_.toThrowable)
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
                   // TODO: Log defects
                   preview.onRun.absorb.forkDaemon *> reset
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

  def invoke(effect: => Unit): UIO[Unit] =
    UIO(Display.getDefault.asyncExec(() => effect))

  def invokeReturn[A](effect: => A): Task[A] =
    Task.effectAsyncM { cb =>
      invoke {
        val evaluatedEffect = effect
        cb(UIO(evaluatedEffect))
      }
    }

  def reset: UIO[Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- invoke {
             terminal.inputBox.setText("")
           }
      _ <- searchResultsRef.set(SearchResults.empty)
    } yield ()

  def deactivate: RIO[Has[Tools] with Blocking, Unit] =
    ZIO.whenCase(OS.os) { case OS.MacOS =>
      Tools.hide
    }

  def clearScreen: UIO[Unit] =
    UIO {
      terminal.outputBox.setText("")
    }

  def open: UIO[Unit] =
    for {
      _ <- invoke {
             val bounds = terminal.shell.getDisplay.getPrimaryMonitor.getClientArea
             val x = (bounds.width - terminal.shell.getSize.x) / 2
             terminal.shell.setLocation(x, 0)
             terminal.shell.open()
           }
    } yield ()

  def hide: UIO[Unit] = invoke(terminal.shell.setVisible(false))

  def activate: UIO[Unit] =
    invoke(terminal.shell.forceActive())

  def opacity: RIO[Env, Float] = invokeReturn(terminal.shell.getAlpha / 255.0f)

  def setOpacity(opacity: Float): RIO[Env, Unit] = invoke {
    terminal.shell.setAlpha((255 * opacity).toInt)
  }.whenM(isOpacitySupported)

  def isOpacitySupported: URIO[Env, Boolean] = UIO(true)

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
      _ <- invoke {
             val preferredFont = terminal.getPreferredFont(config.display.fonts)
             terminal.inputBox.setFont(preferredFont)
             terminal.outputBox.setFont(preferredFont)
           }
    } yield ()
}

object SwtTerminal {

  def create(runtime: CCRuntime, terminal: RawSwtTerminal): RManaged[Env, SwtTerminal] =
    for {
      debounceDelay    <- Conf.get(_.general.debounceDelay).toManaged_
      searchDebouncer  <- Debouncer.make[Env, Nothing, Unit](debounceDelay).toManaged_
      commandCursorRef <- Ref.makeManaged(0)
      searchResultsRef <- Ref.makeManaged(SearchResults.empty[Any])
      swtTerminal = new SwtTerminal(commandCursorRef, searchResultsRef, searchDebouncer, terminal)(runtime)
      _ <- swtTerminal.init.toManaged_
    } yield swtTerminal
}

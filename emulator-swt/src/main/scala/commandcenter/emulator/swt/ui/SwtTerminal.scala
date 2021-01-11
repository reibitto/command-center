package commandcenter.emulator.swt.ui

import commandcenter.CCRuntime.Env
import commandcenter._
import commandcenter.command.{ Command, CommandResult, PreviewResult, SearchResults }
import commandcenter.emulator.swt.event.KeyboardShortcutUtil
import commandcenter.emulator.util.Lists
import commandcenter.locale.Language
import commandcenter.tools.Tools
import commandcenter.ui.CCTheme
import commandcenter.util.{ Debouncer, OS }
import commandcenter.view.Rendered
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.{ KeyAdapter, KeyEvent, ModifyEvent, ModifyListener }
import org.eclipse.swt.widgets.Display
import zio._
import zio.blocking.Blocking
import zio.duration._

import java.awt.Dimension
import scala.collection.mutable

final case class SwtTerminal(
  var config: CCConfig, // TODO: Convert to Ref
  commandCursorRef: Ref[Int],
  searchResultsRef: Ref[SearchResults[Any]],
  searchDebouncer: Debouncer[Env, Nothing, Unit],
  terminal: RawSwtTerminal
)(implicit runtime: Runtime[Env])
    extends GuiTerminal {
  val terminalType: TerminalType = TerminalType.Swt

  val theme = CCTheme.default

  def init: RIO[Env, Unit] =
    setOpacity(config.display.opacity)

  Display.getDefault.asyncExec { () =>
    terminal.inputBox.addModifyListener(new ModifyListener {
      override def modifyText(e: ModifyEvent): Unit = {
        val searchTerm = terminal.inputBox.getText
        val context    = CommandContext(Language.detect(searchTerm), SwtTerminal.this, 1.0)

        runtime.unsafeRunAsync_ {
          searchDebouncer(
            Command
              .search(config.commands, config.aliases, searchTerm, context)
              .tap(r => commandCursorRef.set(0) *> searchResultsRef.set(r) *> render(r))
              .unit
          ).flatMap(_.join)
        }
      }
    })

    terminal.inputBox.addKeyListener(new KeyAdapter {
      override def keyPressed(e: KeyEvent): Unit =
        e.keyCode match {
          case SWT.CR =>
            runtime.unsafeRunAsync_ {
              for {
                _               <- hide
                _               <- deactivate.ignore
                _               <- searchDebouncer.triggerNowAwait
                previousResults <- searchResultsRef.get
                cursorIndex     <- commandCursorRef.get
                resultOpt       <- runSelected(previousResults, cursorIndex).catchAll(_ => UIO.none)
                _               <- ZIO.whenCase(resultOpt) {
                                     case Some(o) if o.result == CommandResult.Exit =>
                                       for {
                                         _ <- hide.ignore
                                         _ <- UIO(System.exit(0))
                                       } yield ()
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
                _               <- commandCursorRef.update(cursor => (cursor + 1) min (previousResults.previews.length - 1))
                _               <- render(previousResults)
              } yield ()
            }

          case SWT.ARROW_UP =>
            e.doit = false // Not ideal to have it outside the for-comprehension, but wrapping this in UIO will not work

            runtime.unsafeRunAsync_ {
              for {
                previousResults <- searchResultsRef.get
                _               <- commandCursorRef.update(cursor => (cursor - 1) max 0)
                _               <- render(previousResults)
              } yield ()
            }

          case _ =>
            runtime.unsafeRunAsync_ {
              for {
                previousResults <- searchResultsRef.get
                shortcutPressed  = KeyboardShortcutUtil.fromKeyEvent(e)
                eligibleResults  = previousResults.previews.filter { p =>
                                     p.source.shortcuts.contains(shortcutPressed)
                                   }
                bestMatch        = eligibleResults.maxByOption(_.score)
                _               <- ZIO.foreach_(bestMatch) { preview =>
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

  private def render(searchResults: SearchResults[Any]): UIO[Unit] = {
    var scrollToPosition: Int = 0

    for {
      commandCursor <- commandCursorRef.get
      buffer         = new StringBuilder
      styles         = new mutable.ArrayDeque[StyleRange]()
      _              = {
        def renderBar(rowIndex: Int): Unit = {
          val barColor = if (rowIndex == commandCursor) terminal.green else terminal.darkGray

          styles.append(new StyleRange(buffer.length, 1, terminal.black, barColor))
          buffer.append("  ")
        }

        def colorMask(width: Int): Long = ~0L >>> (64 - width)

        searchResults.rendered.zipWithIndex.foreach { case (r, row) =>
          r match {
            case Rendered.Styled(segments) => ()

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

                buffer.append(s)

                val swtForegroundOpt = terminal.fromFansiColorCode(ansiForeground.toInt)
                val swtBackgroundOpt = terminal.fromFansiColorCode(ansiBackground.toInt)

                (swtForegroundOpt, swtBackgroundOpt) match {
                  case (None, None) =>
                  // Don't bother wastefully creating a StyleRange object

                  case _ =>
                    styles.append(
                      new StyleRange(
                        buffer.length - s.length,
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

      _ <- invoke {
             if (terminal.shell.isVisible) {
               if (buffer.isEmpty) {
                 terminal.outputBox.setVisible(false)
                 terminal.outputBoxGridData.exclude = true
                 terminal.outputBox.setText("")
               } else {
                 terminal.outputBox.setVisible(true)
                 terminal.outputBoxGridData.exclude = false
                 terminal.outputBox.setText(buffer.toString)

                 terminal.outputBox.setStyleRanges(styles.toArray)
               }

               val newSize = terminal.shell.computeSize(config.display.width, SWT.DEFAULT)
               terminal.shell.setSize(config.display.width, newSize.y min config.display.maxHeight)

               terminal.outputBox.setSelection(scrollToPosition)
             }
           }
    } yield ()
  }

  def runSelected(results: SearchResults[Any], cursorIndex: Int): RIO[Env, Option[PreviewResult[Any]]] = {
    val previewResult = results.previews.lift(cursorIndex)

    for {
      _ <- ZIO.foreach_(previewResult) { preview =>
             // TODO: Log defects
             preview.onRun.absorb.forkDaemon
           }
      _ <- reset
    } yield previewResult
  }

  def invoke(effect: => Unit): UIO[Unit] =
    UIO(Display.getDefault.asyncExec(() => effect))

  def invokeReturn[A](effect: => A): Task[A] =
    Task.effectAsync { cb =>
      Display.getDefault.asyncExec { () =>
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

  def deactivate: RIO[Tools with Blocking, Unit] =
    OS.os match {
      case OS.MacOS => tools.hide
      case _        => UIO.unit
    }

  def clearScreen: UIO[Unit] =
    UIO {
      terminal.outputBox.setText("")
    }

  def open: UIO[Unit] =
    for {
      _ <- invoke {
             val bounds = terminal.shell.getDisplay.getPrimaryMonitor.getClientArea
             val x      = (bounds.width - terminal.shell.getSize.x) / 2
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
  }

  def isOpacitySupported: URIO[Env, Boolean] = UIO(true)

  def size: RIO[Env, Dimension] =
    invokeReturn {
      val size = terminal.shell.getSize
      new Dimension(size.x, size.y)
    }

  def setSize(width: Int, height: Int): RIO[Env, Unit] = invoke(terminal.shell.setSize(width, height))

  def reload: RIO[Env, Unit] = CCConfig.load.use { newConfig =>
    UIO {
      config = newConfig
    }
  }

}

object SwtTerminal {
  def create(config: CCConfig, runtime: CCRuntime, terminal: RawSwtTerminal): ZManaged[Env, Throwable, SwtTerminal] =
    for {
      searchDebouncer  <- Debouncer.make[Env, Nothing, Unit](200.millis).toManaged_
      commandCursorRef <- Ref.makeManaged(0)
      searchResultsRef <- Ref.makeManaged(SearchResults.empty[Any])
      swtTerminal       = new SwtTerminal(config, commandCursorRef, searchResultsRef, searchDebouncer, terminal)(runtime)
      _                <- swtTerminal.init.toManaged_
    } yield swtTerminal
}

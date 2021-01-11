package commandcenter.ui

import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.{ KeyStroke, KeyType }
import com.googlecode.lanterna.screen.Screen.RefreshType
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.{ DefaultTerminalFactory, Terminal }
import com.googlecode.lanterna.{ TerminalPosition, TerminalSize, TerminalTextUtils }
import commandcenter.CCRuntime.Env
import commandcenter.command.{ Command, CommandResult, PreviewResult, SearchResults }
import commandcenter.locale.Language
import commandcenter.util.{ Debouncer, TextUtils }
import commandcenter.view.Rendered
import commandcenter.{ CCConfig, CCTerminal, CommandContext, TerminalType }
import zio._
import zio.blocking._
import zio.duration._

import java.awt.Dimension

final case class CliTerminal[T <: Terminal](
  terminal: T,
  screen: TerminalScreen,
  graphics: TextGraphics,
  configRef: Ref[CCConfig],
  commandCursorRef: Ref[Int],
  textCursorRef: Ref[TextCursor],
  searchResultsRef: Ref[SearchResults[Any]],
  keyHandlersRef: Ref[Map[KeyStroke, URIO[Env, EventResult]]],
  searchDebouncer: Debouncer[Env, Nothing, Unit],
  renderQueue: Queue[SearchResults[Any]],
  buffer: StringBuilder
) extends CCTerminal {

  val prompt: String             = "> "
  val terminalType: TerminalType = TerminalType.Cli

  def opacity: RIO[Env, Float] = UIO(1.0f)

  def setOpacity(opacity: Float): RIO[Env, Unit] = ZIO.unit

  def isOpacitySupported: URIO[Env, Boolean] = UIO(false)

  def size: RIO[Env, Dimension] = UIO(new Dimension(screen.getTerminalSize.getColumns, screen.getTerminalSize.getRows))

  def setSize(width: Int, height: Int): RIO[Env, Unit] = ZIO.unit

  def reload: RIO[Env, Unit] =
    CCConfig.load.use { newConfig =>
      configRef.set(newConfig)
    }

  def defaultKeyHandlers: Map[KeyStroke, URIO[Env, EventResult]] =
    Map(
      new KeyStroke(KeyType.Enter)      -> (for {
        _                  <- searchDebouncer.triggerNowAwait
        index              <- commandCursorRef.get
        previousResults    <- searchResultsRef.get
        maybePreviewResult <- runSelected(previousResults, index)
      } yield maybePreviewResult.map(_.result) match {
        case Some(CommandResult.Exit) => EventResult.Exit
        case _                        => EventResult.Success
      }).catchAll(t => UIO(EventResult.UnexpectedError(t))),
      new KeyStroke(KeyType.Backspace)  -> (for {
        currentCursor <- textCursorRef.get
        _             <- if (buffer.nonEmpty && currentCursor.logical.column > 0) {
                           val delta = buffer.lift(currentCursor.logical.column - 1).map(TextUtils.charWidth).getOrElse(0)

                           textCursorRef.update(_.offsetColumnBy(-1, -delta)) *> UIO(
                             buffer.deleteCharAt(currentCursor.logical.column - 1)
                           )
                         } else
                           UIO(currentCursor)
      } yield EventResult.Success),
      new KeyStroke(KeyType.Delete)     -> (for {
        currentCursor <- textCursorRef.get
        _             <- if (buffer.nonEmpty && currentCursor.logical.column < buffer.length)
                           UIO(buffer.deleteCharAt(currentCursor.logical.column))
                         else
                           UIO(currentCursor)
      } yield EventResult.Success),
      new KeyStroke(KeyType.ArrowDown)  -> (
        for {
          previousResults <- searchResultsRef.get
          _               <- commandCursorRef.update(cursor => (cursor + 1) min (previousResults.previews.length - 1))
        } yield EventResult.Success
      ),
      new KeyStroke(KeyType.ArrowUp)    -> commandCursorRef.update(cursor => (cursor - 1) max 0).as(EventResult.Success),
      new KeyStroke(KeyType.ArrowLeft)  -> (
        for {
          currentCursor <- textCursorRef.get
          _             <- if (currentCursor.logical.column > 0) {
                             val delta = buffer.lift(currentCursor.logical.column - 1).map(TextUtils.charWidth).getOrElse(0)
                             textCursorRef.update(_.offsetColumnBy(-1, -delta))
                           } else
                             UIO(currentCursor)
        } yield EventResult.Success
      ),
      new KeyStroke(KeyType.ArrowRight) -> (
        for {
          currentCursor <- textCursorRef.get
          _             <- if (currentCursor.logical.column < buffer.length) {
                             val delta = buffer.lift(currentCursor.logical.column).map(TextUtils.charWidth).getOrElse(0)
                             textCursorRef.update(_.offsetColumnBy(1, delta))
                           } else
                             UIO(currentCursor)
        } yield EventResult.Success
      )
    )

  def reset(): UIO[Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- textCursorRef.set(TextCursor.unit)
      _ <- searchResultsRef.set(SearchResults.empty)
      _  = screen.clear()
      _  = buffer.clear()
    } yield ()

  def search(commands: Vector[Command[Any]], aliases: Map[String, List[String]])(
    searchTerm: String
  ): URIO[Env, Unit] = {
    val context = CommandContext(Language.detect(searchTerm), this, 1.0)

    Command
      .search(commands, aliases, searchTerm, context)
      .tap { r =>
        // TODO: Make resetCursorOnChange customizable
        commandCursorRef.set(0) *> searchResultsRef.set(r) *> renderQueue.offer(r)
      }
      .unit
  }

  def render(results: SearchResults[Any]): Task[Unit] =
    for {
      _             <- UIO(screen.clear())
      commandCursor <- commandCursorRef.get
      size          <- terminalSize
      rows           = size.getRows
      columns        = size.getColumns
      _             <- ZIO.foldLeft(results.rendered.take(rows - 1))(Cursor(prompt.length, 1))((s, r) => printRendered(r, s))
      _             <- Task.when(results.previews.nonEmpty) {
                         Task {
                           val cursorRow = commandCursor + 1

                           results.rendered.foldLeft((1, 1)) { case ((ci, di), d) =>
                             val plainText = d match {
                               case styled: Rendered.Styled => styled.plainText
                               case Rendered.Ansi(ansiStr)  => ansiStr.plainText
                             }

                             val lines = plainText.linesIterator.toList

                             val renderedLine =
                               if (ci == cursorRow)
                                 fansi.Back.Green(" ").render
                               else
                                 fansi.Back.Black(" ").render

                             lines.indices.foreach(offset => graphics.putCSIStyledString(0, di + offset, renderedLine))

                             (ci + 1, di + plainText.linesIterator.length)
                           }
                         }
                       }
      placeholder    = if (buffer.isEmpty) fansi.Color.Black(" Search").overlay(fansi.Bold.On) else fansi.Str("")
      _             <- putAnsiStr(0, 0, fansi.Color.Cyan("> ") ++ placeholder)
      bufferDisplay  = buffer.toString.takeRight(columns - prompt.length)
      _             <- putAnsiStr(prompt.length, 0, fansi.Color.White(bufferDisplay))
      textCursor    <- textCursorRef.get
      _             <- Task(
                         screen
                           .setCursorPosition(new TerminalPosition(prompt.length + (textCursor.actual.column min columns), 0))
                       )
      _             <- swapBuffer
    } yield ()

  def runSelected(results: SearchResults[Any], cursorIndex: Int): RIO[Env, Option[PreviewResult[Any]]] = {
    val previewResult = results.previews.lift(cursorIndex)

    for {
      _ <- ZIO.foreach_(previewResult) { preview =>
             // TODO: Log defects
             preview.onRun.absorb.forkDaemon
           }
      _ <- reset()
    } yield previewResult
  }

  def processEvent(commands: Vector[Command[Any]], aliases: Map[String, List[String]]): URIO[Env, EventResult] =
    (for {
      keyStroke          <- readInput
      previousResults    <- searchResultsRef.get
      eventResult        <- for {
                              handlers <- keyHandlersRef.get
                              result   <- handlers.getOrElse(keyStroke, UIO(EventResult.Success))
                            } yield result
      _                  <- Option(keyStroke.getCharacter).fold(UIO.unit) { c =>
                              UIO.when(!TerminalTextUtils.isControlCharacter(c)) {
                                for {
                                  cursor <- textCursorRef.getAndUpdate(_.offsetColumnBy(1, TextUtils.charWidth(c)))
                                  _      <- UIO(buffer.insert(cursor.logical.column, c))
                                } yield ()
                              }
                            }
      searchTerm          = buffer.toString
      // Render the user input change right away so there is immediate feedback. Rendering the new search results will
      // come later when they're ready.
      resultsWithNewInput = previousResults.copy(searchTerm = searchTerm)
      _                  <- renderQueue.offer {
                              resultsWithNewInput
                            } // TODO: Add option for re-rendering only the input textfield and so on. Or auto-detect that case
      _                  <- searchResultsRef.set(resultsWithNewInput)
      _                  <- if (previousResults.hasChange(searchTerm))
                              searchDebouncer(search(commands, aliases)(searchTerm)).flatMap(_.join).forkDaemon
                            else
                              UIO(previousResults)
      _                  <- textCursorRef.get
    } yield eventResult).catchAll(t => UIO(EventResult.UnexpectedError(t)))

  def readInput: RIO[Blocking, KeyStroke] =
    effectBlocking {
      terminal.readInput()
    }

  def terminalSize: Task[TerminalSize] =
    Task(terminal.getTerminalSize)

  def swapBuffer: Task[Unit] =
    Task(screen.refresh(RefreshType.DELTA))

  def putAnsiStr(str: fansi.Str): UIO[Unit] =
    for {
      cursor <- textCursorRef.get
      _      <- UIO(graphics.putCSIStyledString(cursor.actual.column, cursor.actual.row, str.render))
    } yield ()

  def putAnsiStr(column: Int, row: Int, str: fansi.Str): UIO[Unit] =
    UIO(graphics.putCSIStyledString(column, row, str.render))

  def putAnsiStrRaw(ansiString: String, cursor: Cursor): UIO[Unit] =
    UIO(graphics.putCSIStyledString(cursor.column, cursor.row, ansiString))

  def printAnsiStr(string: fansi.Str, cursor: Cursor): UIO[Cursor] = {
    val lines = string.render.linesIterator.toVector
    for {
      _ <- ZIO.foreach_(lines.zipWithIndex) { case (s, i) =>
             putAnsiStrRaw(s, cursor + Cursor(0, i))
           }
    } yield cursor + Cursor(0, lines.length)
  }

  def printRendered(rendered: Rendered, cursor: Cursor): UIO[Cursor] =
    rendered match {
      case Rendered.Ansi(ansiStr)  => printAnsiStr(ansiStr, cursor)
      // TODO: Do a best effort conversion (i.e. preserve colors, but ignore font settings)
      case styled: Rendered.Styled => printAnsiStr(styled.plainText, cursor)
    }

  // TODO: Will probably also need to clear on resize size Delta rendering is used
//  terminal.addResizeListener()
}

object CliTerminal {
  def createNative(config: CCConfig): Managed[Throwable, CliTerminal[Terminal]] =
    create(config) {
      val terminalFactory = new DefaultTerminalFactory()

      ZManaged.fromAutoCloseable(Task {
        terminalFactory.createHeadlessTerminal()
      })
    }

  private def create[T <: Terminal](
    config: CCConfig
  )(managedTerminal: Managed[Throwable, T]): Managed[Throwable, CliTerminal[T]] =
    for {
      terminal         <- managedTerminal
      screen           <- ZManaged.fromAutoCloseable(Task(new TerminalScreen(terminal)))
      graphics         <- Task(screen.newTextGraphics()).toManaged_
      configRef        <- Ref.makeManaged(config)
      commandCursorRef <- Ref.makeManaged(0)
      textCursorRef    <- Ref.makeManaged(TextCursor.unit)
      searchResultsRef <- Ref.makeManaged(SearchResults.empty[Any])
      keyHandlersRef   <- Ref.makeManaged(Map.empty[KeyStroke, URIO[Env, EventResult]])
      searchDebouncer  <- Debouncer.make[Env, Nothing, Unit](200.millis).toManaged_
      renderQueue      <- Queue.sliding[SearchResults[Any]](1).toManaged_
    } yield CliTerminal(
      terminal,
      screen,
      graphics,
      configRef,
      commandCursorRef,
      textCursorRef,
      searchResultsRef,
      keyHandlersRef,
      searchDebouncer,
      renderQueue,
      new StringBuilder()
    )
}

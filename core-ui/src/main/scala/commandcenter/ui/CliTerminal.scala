package commandcenter.ui

import com.googlecode.lanterna.{TerminalPosition, TerminalSize, TerminalTextUtils}
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.{KeyStroke, KeyType}
import com.googlecode.lanterna.screen.Screen.RefreshType
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.{DefaultTerminalFactory, Terminal}
import commandcenter.{CCTerminal, CommandContext, Conf, TerminalType}
import commandcenter.command.*
import commandcenter.locale.Language
import commandcenter.util.{Debouncer, TextUtils}
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import zio.*
import zio.stream.ZSink
import zio.ZIO.attemptBlocking

import java.awt.Dimension

final case class CliTerminal[T <: Terminal](
  terminal: T,
  screen: TerminalScreen,
  graphics: TextGraphics,
  commandCursorRef: Ref[Int],
  textCursorRef: Ref[TextCursor],
  searchResultsRef: Ref[SearchResults[Any]],
  keyHandlersRef: Ref[Map[KeyStroke, URIO[Env, EventResult]]],
  searchDebouncer: Debouncer[Env, Nothing, Unit],
  renderQueue: Queue[SearchResults[Any]],
  buffer: StringBuilder
) extends CCTerminal {

  val prompt: String = "> "
  val terminalType: TerminalType = TerminalType.Cli

  def opacity: RIO[Env, Float] = ZIO.succeed(1.0f)

  def setOpacity(opacity: Float): RIO[Env, Unit] = ZIO.unit

  def isOpacitySupported: URIO[Env, Boolean] = ZIO.succeed(false)

  def size: RIO[Env, Dimension] =
    ZIO.succeed(new Dimension(screen.getTerminalSize.getColumns, screen.getTerminalSize.getRows))

  def setSize(width: Int, height: Int): RIO[Env, Unit] = ZIO.unit

  def reload: RIO[Env, Unit] = Conf.reload.unit

  def defaultKeyHandlers: Map[KeyStroke, URIO[Env, EventResult]] =
    Map(
      new KeyStroke(KeyType.Enter) -> (for {
        _                  <- searchDebouncer.triggerNowAwait
        previousResults    <- searchResultsRef.get
        cursorIndex        <- commandCursorRef.get
        maybePreviewResult <- runSelected(previousResults, cursorIndex)
      } yield maybePreviewResult.map(_.runOption) match {
        case Some(RunOption.Exit)       => EventResult.Exit
        case Some(RunOption.RemainOpen) => EventResult.RemainOpen
        case _                          => EventResult.Success
      }).catchAll(t => ZIO.succeed(EventResult.UnexpectedError(t))),
      new KeyStroke(KeyType.Backspace) -> (for {
        currentCursor <- textCursorRef.get
        _ <- if (buffer.nonEmpty && currentCursor.logical.column > 0) {
               val delta = buffer.lift(currentCursor.logical.column - 1).map(TextUtils.charWidth).getOrElse(0)

               textCursorRef.update(_.offsetColumnBy(-1, -delta)) *> ZIO.succeed(
                 buffer.deleteCharAt(currentCursor.logical.column - 1)
               )
             } else
               ZIO.succeed(currentCursor)
      } yield EventResult.Success),
      new KeyStroke(KeyType.Escape) -> ZIO.succeed(EventResult.Exit),
      new KeyStroke(KeyType.Delete) -> (for {
        currentCursor <- textCursorRef.get
        _ <- if (buffer.nonEmpty && currentCursor.logical.column < buffer.length)
               ZIO.succeed(buffer.deleteCharAt(currentCursor.logical.column))
             else
               ZIO.succeed(currentCursor)
      } yield EventResult.Success),
      new KeyStroke(KeyType.ArrowDown) -> (
        for {
          previousResults <- searchResultsRef.get
          _               <- commandCursorRef.update(cursor => (cursor + 1) min (previousResults.previews.length - 1))
        } yield EventResult.Success
      ),
      new KeyStroke(KeyType.ArrowUp) -> commandCursorRef.update(cursor => (cursor - 1) max 0).as(EventResult.Success),
      new KeyStroke(KeyType.ArrowLeft) -> (
        for {
          currentCursor <- textCursorRef.get
          _ <- if (currentCursor.logical.column > 0) {
                 val delta = buffer.lift(currentCursor.logical.column - 1).map(TextUtils.charWidth).getOrElse(0)
                 textCursorRef.update(_.offsetColumnBy(-1, -delta))
               } else
                 ZIO.succeed(currentCursor)
        } yield EventResult.Success
      ),
      new KeyStroke(KeyType.ArrowRight) -> (
        for {
          currentCursor <- textCursorRef.get
          _ <- if (currentCursor.logical.column < buffer.length) {
                 val delta = buffer.lift(currentCursor.logical.column).map(TextUtils.charWidth).getOrElse(0)
                 textCursorRef.update(_.offsetColumnBy(1, delta))
               } else
                 ZIO.succeed(currentCursor)
        } yield EventResult.Success
      )
    )

  def reset: UIO[Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- textCursorRef.set(TextCursor.unit)
      _ <- searchResultsRef.set(SearchResults.empty)
      _ = screen.clear()
      _ = buffer.clear()
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
      _             <- ZIO.succeed(screen.clear())
      commandCursor <- commandCursorRef.get
      size          <- terminalSize
      rows = size.getRows
      columns = size.getColumns
      _ <- ZIO.foldLeft(results.rendered.take(rows - 1))(Cursor(prompt.length, 1))((s, r) => printRendered(r, s))
      _ <- ZIO.when(results.previews.nonEmpty) {
             ZIO.attempt {
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
      placeholder = if (buffer.isEmpty) fansi.Color.Black(" Search").overlay(fansi.Bold.On) else fansi.Str("")
      _ <- putAnsiStr(0, 0, fansi.Color.Cyan("> ") ++ placeholder)
      bufferDisplay = buffer.toString.takeRight(columns - prompt.length)
      _          <- putAnsiStr(prompt.length, 0, fansi.Color.White(bufferDisplay))
      textCursor <- textCursorRef.get
      _ <- ZIO.attempt(
             screen
               .setCursorPosition(new TerminalPosition(prompt.length + (textCursor.actual.column min columns), 0))
           )
      _ <- swapBuffer
    } yield ()

  def runSelected(results: SearchResults[Any], cursorIndex: Int): RIO[Env, Option[PreviewResult[Any]]] =
    ZIO.foreach(results.previews.lift(cursorIndex)) { preview =>
      for {
        _ <- preview.moreResults match {
               case MoreResults.Remaining(p @ PreviewResults.Paginated(rs, pageSize, totalRemaining))
                   if totalRemaining.forall(_ > 0) =>
                 for {
                   _ <- preview.onRun.absorb.forkDaemon
                   (results, restStream) <- ZIO.scoped {
                                              rs.peel(ZSink.take[PreviewResult[Any]](pageSize)).mapError(_.toThrowable)
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
                 // TODO: Log defects
                 preview.onRun.absorb.forkDaemon *> reset
             }
      } yield preview
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
      _ <- renderQueue.offer(searchResults)
    } yield ()

  def processEvent(commands: Vector[Command[Any]], aliases: Map[String, List[String]]): URIO[Env, EventResult] =
    (for {
      keyStroke       <- readInput
      previousResults <- searchResultsRef.get
      eventResult <- for {
                       handlers <- keyHandlersRef.get
                       result   <- handlers.getOrElse(keyStroke, ZIO.succeed(EventResult.Success))
                     } yield result
      _ <- ZIO.foreachDiscard(Option(keyStroke.getCharacter)) { c =>
             ZIO.when(!TerminalTextUtils.isControlCharacter(c)) {
               for {
                 cursor <- textCursorRef.getAndUpdate(_.offsetColumnBy(1, TextUtils.charWidth(c)))
                 _      <- ZIO.succeed(buffer.insert(cursor.logical.column, c))
               } yield ()
             }
           }
      searchTerm = buffer.toString
      // Render the user input change right away so there is immediate feedback. Rendering the new search results will
      // come later when they're ready.
      resultsWithNewInput = previousResults.copy(searchTerm = searchTerm)
      // TODO: Add option for re-rendering only the input textfield and so on. Or auto-detect that case
      _ <- ZIO.when(eventResult == EventResult.Success) {
             searchResultsRef.set(resultsWithNewInput) *> renderQueue.offer(resultsWithNewInput)
           }
      _ <- ZIO.when(previousResults.hasChange(searchTerm)) {
             searchDebouncer(search(commands, aliases)(searchTerm)).flatMap(_.join).forkDaemon
           }
      _ <- textCursorRef.get
    } yield eventResult).catchAll(t => ZIO.succeed(EventResult.UnexpectedError(t)))

  def readInput: RIO[Any, KeyStroke] =
    attemptBlocking {
      terminal.readInput()
    }

  def terminalSize: Task[TerminalSize] =
    ZIO.attempt(terminal.getTerminalSize)

  def swapBuffer: Task[Unit] =
    ZIO.attempt(screen.refresh(RefreshType.DELTA))

  def putAnsiStr(str: fansi.Str): UIO[Unit] =
    for {
      cursor <- textCursorRef.get
      _      <- ZIO.succeed(graphics.putCSIStyledString(cursor.actual.column, cursor.actual.row, str.render))
    } yield ()

  def putAnsiStr(column: Int, row: Int, str: fansi.Str): UIO[Unit] =
    ZIO.succeed(graphics.putCSIStyledString(column, row, str.render))

  def putAnsiStrRaw(ansiString: String, cursor: Cursor): UIO[Unit] =
    ZIO.succeed(graphics.putCSIStyledString(cursor.column, cursor.row, ansiString))

  def printAnsiStr(string: fansi.Str, cursor: Cursor): UIO[Cursor] = {
    val lines = string.render.linesIterator.toVector
    for {
      _ <- ZIO.foreachDiscard(lines.zipWithIndex) { case (s, i) =>
             putAnsiStrRaw(s, cursor + Cursor(0, i))
           }
    } yield cursor + Cursor(0, lines.length)
  }

  def printRendered(rendered: Rendered, cursor: Cursor): UIO[Cursor] =
    rendered match {
      case Rendered.Ansi(ansiStr) => printAnsiStr(ansiStr, cursor)
      // TODO: Do a best effort conversion (i.e. preserve colors, but ignore font settings)
      case styled: Rendered.Styled => printAnsiStr(styled.plainText, cursor)
    }

  // TODO: Will probably also need to clear on resize size Delta rendering is used
//  terminal.addResizeListener()
}

object CliTerminal {

  def createNative: RIO[Scope & Conf, CliTerminal[Terminal]] =
    create {
      val terminalFactory = new DefaultTerminalFactory()

      ZIO.fromAutoCloseable(ZIO.attempt {
        terminalFactory.createHeadlessTerminal()
      })
    }

  private def create[T <: Terminal](managedTerminal: RIO[Scope, T]): RIO[Scope & Conf, CliTerminal[T]] =
    for {
      terminal         <- managedTerminal
      screen           <- ZIO.fromAutoCloseable(ZIO.attempt(new TerminalScreen(terminal)))
      graphics         <- ZIO.attempt(screen.newTextGraphics())
      commandCursorRef <- Ref.make(0)
      textCursorRef    <- Ref.make(TextCursor.unit)
      searchResultsRef <- Ref.make(SearchResults.empty[Any])
      keyHandlersRef   <- Ref.make(Map.empty[KeyStroke, URIO[Env, EventResult]])
      debounceDelay    <- Conf.get(_.general.debounceDelay)
      searchDebouncer  <- Debouncer.make[Env, Nothing, Unit](debounceDelay)
      renderQueue      <- Queue.sliding[SearchResults[Any]](1)
    } yield CliTerminal(
      terminal,
      screen,
      graphics,
      commandCursorRef,
      textCursorRef,
      searchResultsRef,
      keyHandlersRef,
      searchDebouncer,
      renderQueue,
      new StringBuilder()
    )
}

package commandcenter

import commandcenter.command.*
import commandcenter.event.KeyboardShortcut
import commandcenter.locale.Language
import commandcenter.tools.Tools
import commandcenter.util.{Debouncer, OS}
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import fansi.Color
import zio.*

abstract class BaseGuiTerminal extends GuiTerminal {

  def searchDebouncer: Debouncer[Env, Nothing, Unit]
  def commandCursorRef: Ref[Int]
  def searchResultsRef: Ref[SearchResults[Any]]

  // Bumped on every open and hide so a pending delayed reopen can detect it was superseded
  private val windowGeneration: Ref[Long] = Unsafe.unsafe(implicit u => Ref.unsafe.make(0L))

  def init: RIO[Env, Unit] = Conf.get(_.display.opacity).flatMap(setOpacity)

  final def hide: URIO[Env, Unit] = windowGeneration.update(_ + 1) *> hideWindow

  protected def hideWindow: URIO[Env, Unit]

  def setResults(results: SearchResults[Any]): URIO[Env, Unit] =
    replaceResults(results) *> render(results)

  // Releases the previous results, closing any streams they kept open for "More"
  protected def replaceResults(results: SearchResults[Any]): UIO[Unit] =
    commandCursorRef.set(0) *> searchResultsRef.getAndSet(results).flatMap(_.release)

  protected def render(searchResults: SearchResults[Any]): URIO[Env, Unit]

  protected def resetAndHide: ZIO[Env, Nothing, Unit] = hide *> deactivate.ignore *> reset

  protected def onInputChanged(searchTerm: String): URIO[Env, Unit] =
    for {
      config        <- Conf.config
      searchResults <- searchResultsRef.get
      // This is an optimization but it also helps with certain IMEs where they resend all the changes when
      // exiting out of composition mode (committing the changes).
      sameAsLast = searchResults.searchTerm == searchTerm && searchTerm.nonEmpty
      context = CommandContext(Language.detect(searchTerm), this, 1.0)
      // Uninterruptible once the search is done, so the debouncer can't drop (and leak) completed results
      _ <- searchDebouncer(
             ZIO
               .uninterruptibleMask(restore =>
                 restore(Command.search(config.commands, config.aliases, searchTerm, context)).tap(replaceResults)
               )
               .flatMap(render)
               .unless(sameAsLast)
               .unit
           )
    } yield ()

  // Key actions run either on key press or key release, depending on `hideOnKeyRelease`.
  protected def onPress(effect: URIO[Env, Any]): URIO[Env, Unit] =
    effect.unlessZIO(Conf.get(_.general.hideOnKeyRelease)).unit

  protected def onRelease(effect: URIO[Env, Any]): URIO[Env, Unit] =
    effect.whenZIO(Conf.get(_.general.hideOnKeyRelease)).unit

  def openAndActivate(reopenDelay: Option[Duration]): RIO[Env, Unit] =
    for {
      generation <- windowGeneration.updateAndGet(_ + 1)
      _          <- open *> activate
      _          <- ZIO.foreachDiscard(reopenDelay) { delay =>
             (open *> activate).whenZIO(windowGeneration.get.map(_ == generation)).delay(delay)
           }
    } yield ()

  // Moves the cursor by `delta`, clamped to the current results. Returns the previous and new cursor.
  protected def moveCursor(delta: Int): UIO[(Int, Int)] =
    searchResultsRef.get.flatMap { results =>
      commandCursorRef.modify { cursor =>
        val next = ((cursor + delta) min (results.previews.length - 1)) max 0
        ((cursor, next), next)
      }
    }

  protected def runShortcut(shortcutPressed: KeyboardShortcut): URIO[Env, Unit] =
    searchResultsRef.get.flatMap { results =>
      results.previews.filter(_.shortcuts.contains(shortcutPressed)).maxByOption(_.score) match {
        case Some(preview) => runPreview(preview)
        case None          => runGlobalShortcut(shortcutPressed)
      }
    }

  // Falls back to a command's own configured `shortcuts` even when the input textbox is empty.
  private def runGlobalShortcut(shortcutPressed: KeyboardShortcut): URIO[Env, Unit] =
    if (shortcutPressed.isEmpty)
      ZIO.unit
    else
      for {
        config <- Conf.config
        commandOpt = config.commands.find(_.shortcuts.contains(shortcutPressed))
        cmdContext = CommandContext(Language.default, this, 1.0)
        previewOpt <- ZIO.foreach(commandOpt)(Command.searchByCommandName(_, cmdContext)).map(_.flatten)
        _          <- ZIO.foreachDiscard(previewOpt)(runPreview)
      } yield ()

  private def runPreview(preview: PreviewResult[Any]): URIO[Env, Unit] = {
    val close = preview.runOption != RunOption.RemainOpen
    hide.when(close) *> preview.onRunSandboxedLogged.forkDaemon *> reset.when(close).unit
  }

  protected def runIndex(results: SearchResults[Any], cursorIndex: Int): RIO[Env, Option[PreviewResult[Any]]] =
    results.previews.lift(cursorIndex) match {
      case None =>
        (hide *> deactivate.ignore).as(None)

      case previewOpt @ Some(preview) =>
        for {
          _ <- (hide *> deactivate.ignore).when(preview.runOption != RunOption.RemainOpen)
          _ <- preview.moreResults match {
                 case more: MoreResults.Remaining[Any] @unchecked if more.totalRemaining.forall(_ > 0) =>
                   for {
                     _                    <- preview.onRunSandboxedLogged.forkDaemon
                     (morePreviews, rest) <- more.nextPage.mapError(_.toThrowable)
                     // The entry becomes the "More" row, with the count updated
                     nextSource = rest.map(r => preview.rendered(Rendered.Ansi(Color.Yellow(r.moreMessage))).moreResults(r))
                     _ <- spliceMore(Some(results), cursorIndex, morePreviews, nextSource)
                   } yield ()

                 case _ =>
                   preview.onRunSandboxedLogged.forkDaemon *> reset.when(preview.runOption != RunOption.RemainOpen)
               }
        } yield previewOpt
    }

  protected def runSelected: ZIO[Env, Nothing, Unit] =
    for {
      _               <- searchDebouncer.triggerNowAwait
      previousResults <- searchResultsRef.get
      cursorIndex     <- commandCursorRef.get
      resultOpt       <- runIndex(previousResults, cursorIndex).orElseSucceed(None)
      _               <- ZIO.whenCase(resultOpt.map(_.runOption)) { case Some(RunOption.Exit) =>
             ZIO.succeed(java.lang.System.exit(0)).forkDaemon
           }
    } yield ()

  def deactivate: RIO[Tools, Unit] =
    ZIO
      .whenCaseDiscard(OS.os) { case OS.MacOS =>
        Tools.hide
      }

  // `nextSource` is the "more" entry to show after the new results, if any remain
  def showMore[A](moreResults: Chunk[PreviewResult[A]], nextSource: Option[PreviewResult[A]]): RIO[Env, Unit] =
    commandCursorRef.get.flatMap(spliceMore(None, _, moreResults, nextSource))

  // Replaces the preview at `index` with the new page. Dropped if the results are no longer `expected`
  // (e.g. a new search landed while the page was being fetched).
  private def spliceMore[A](
      expected: Option[SearchResults[Any]],
      index: Int,
      moreResults: Chunk[PreviewResult[A]],
      nextSource: Option[PreviewResult[A]]
  ): URIO[Env, Unit] =
    searchResultsRef.modify { results =>
      if (expected.exists(_ ne results)) (None, results)
      else {
        val (front, back) = results.previews.splitAt(index)

        val previews =
          if (moreResults.isEmpty)
            front ++ Chunk(PreviewResult.nothing(Rendered.Ansi(Color.Yellow("No results found.")))) ++ back.drop(1)
          else
            front ++ moreResults ++ Chunk.fromIterable(nextSource) ++ back.drop(1)

        val updated = results.copy(previews = previews)
        (Some(updated), updated)
      }
    }.flatMap(ZIO.foreachDiscard(_)(render))

}

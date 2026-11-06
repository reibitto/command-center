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
import zio.stream.ZSink

abstract class BaseGuiTerminal extends GuiTerminal {

  def searchDebouncer: Debouncer[Env, Nothing, Unit]
  def commandCursorRef: Ref[Int]
  def searchResultsRef: Ref[SearchResults[Any]]

  def init: RIO[Env, Unit] = Conf.get(_.display.opacity).flatMap(setOpacity)

  def setResults(results: SearchResults[Any]): URIO[Env, Unit] =
    commandCursorRef.set(0) *> searchResultsRef.set(results) *> render(results)

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
      _ <- searchDebouncer(
             Command
               .search(config.commands, config.aliases, searchTerm, context)
               .tap(setResults)
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
    open *> activate *> ZIO.foreachDiscard(reopenDelay)(delay => (open *> activate).delay(delay))

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
                 case MoreResults.Remaining(p @ PreviewResults.Paginated(rs, initialPageSize, pageSize, totalRemaining))
                     if totalRemaining.forall(_ > 0) =>
                   for {
                     _                     <- preview.onRunSandboxedLogged.forkDaemon
                     (results, restStream) <-
                       Scope.global.use {
                         rs.peel(ZSink.take[PreviewResult[Any]](pageSize))
                           .mapError(_.toThrowable)
                       }
                     source = if (initialPageSize == 1) preview.rendered(Rendered.Ansi(Color.Yellow(p.moreMessage)))
                              else preview
                     _ <- showMore(
                            results,
                            source.moreResults(
                              MoreResults.Remaining(
                                p.copy(results = restStream, totalRemaining = totalRemaining.map(_ - pageSize))
                              )
                            ),
                            pageSize
                          )
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

  def showMore[A](
      moreResults: Chunk[PreviewResult[A]],
      previewSource: PreviewResult[A],
      pageSize: Int
  ): RIO[Env, Unit] =
    for {
      cursorIndex   <- commandCursorRef.get
      searchResults <- searchResultsRef.updateAndGet { results =>
                         val (front, back) = results.previews.splitAt(cursorIndex)

                         val previews =
                           if (moreResults.isEmpty) {
                             val inBetween =
                               PreviewResult
                                 .nothing(Rendered.Ansi(Color.Yellow("No results found.")))

                             front ++ Chunk(inBetween) ++ back.tail
                           } else if (moreResults.length < pageSize)
                             front ++ moreResults ++ back.tail
                           else
                             front ++ moreResults ++ Chunk.single(previewSource) ++ back.tail

                         results.copy(previews = previews)
                       }
      _ <- render(searchResults)
    } yield ()

}

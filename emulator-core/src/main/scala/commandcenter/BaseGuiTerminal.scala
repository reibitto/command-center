package commandcenter

import commandcenter.CCRuntime.Env
import commandcenter.command.{MoreResults, PreviewResult, PreviewResults, RunOption, SearchResults}
import commandcenter.tools.Tools
import commandcenter.util.{Debouncer, OS}
import commandcenter.view.Rendered
import fansi.Color
import zio.*
import zio.stream.ZSink

abstract class BaseGuiTerminal extends GuiTerminal {

  def searchDebouncer: Debouncer[Env, Nothing, Unit]
  def commandCursorRef: Ref[Int]
  def searchResultsRef: Ref[SearchResults[Any]]

  def setResults(results: SearchResults[Any]): URIO[Env, Unit] =
    for {
      _ <- commandCursorRef.set(0)
      _ <- searchResultsRef.set(results)
      _ <- render(results)
    } yield ()

  protected def render(searchResults: SearchResults[Any]): URIO[Env, Unit]

  protected def resetAndHide: ZIO[Env, Nothing, Unit] =
    for {
      _ <- hide
      _ <- deactivate.ignore
      _ <- reset
    } yield ()

  protected def runIndex(results: SearchResults[Any], cursorIndex: Int): RIO[Env, Option[PreviewResult[Any]]] =
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
            case MoreResults.Remaining(p @ PreviewResults.Paginated(rs, initialPageSize, pageSize, totalRemaining))
              if totalRemaining.forall(_ > 0) =>
              for {
                _ <- preview.onRunSandboxedLogged.forkDaemon
                (results, restStream) <-
                  Scope.global.use {
                    rs.peel(ZSink.take[PreviewResult[Any]](pageSize))
                      .mapError(_.toThrowable)
                  }
                _ <- if (initialPageSize == 1)
                  showMore(
                    results,
                    preview
                      .rendered(Rendered.Ansi(Color.Yellow(p.moreMessage)))
                      .moreResults(
                        MoreResults.Remaining(
                          p.copy(
                            results = restStream,
                            totalRemaining = p.totalRemaining.map(_ - pageSize)
                          )
                        )
                      ),
                    pageSize
                  )
                else
                  showMore(
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
              preview.onRunSandboxedLogged.forkDaemon *> reset.when(preview.runOption != RunOption.RemainOpen)
          }
        } yield previewOpt
    }

  protected def runSelected: ZIO[Env, Nothing, Unit] =
    for {
      _               <- searchDebouncer.triggerNowAwait
      previousResults <- searchResultsRef.get
      cursorIndex     <- commandCursorRef.get
      resultOpt       <- runIndex(previousResults, cursorIndex).catchAll(_ => ZIO.none)
      _ <- ZIO.whenCase(resultOpt.map(_.runOption)) { case Some(RunOption.Exit) =>
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
      cursorIndex <- commandCursorRef.get
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

package commandcenter

import java.awt.Dimension

import commandcenter.CCRuntime.Env
import commandcenter.command.{ Command, PreviewResult, SearchResults }
import commandcenter.locale.Language
import zio._

case class HeadlessTerminal(searchResultsRef: Ref[SearchResults[Any]]) extends CCTerminal {
  def terminalType: TerminalType = TerminalType.Test

  def opacity: RIO[Env, Float] = UIO(1.0f)

  def setOpacity(opacity: Float): RIO[Env, Unit] = ZIO.unit

  def isOpacitySupported: URIO[Env, Boolean] = UIO(false)

  def size: RIO[Env, Dimension] = UIO(new Dimension(80, 40))

  def setSize(width: Int, height: Int): RIO[Env, Unit] = ZIO.unit

  def reload: RIO[Env, Unit] = ZIO.unit

  def search(commands: Vector[Command[Any]], aliases: Map[String, List[String]])(
    searchTerm: String
  ): URIO[Env, SearchResults[Any]] = {
    val context = CommandContext(Language.detect(searchTerm), this, 1.0)

    Command
      .search(commands, aliases, searchTerm, context)
      .tap { r =>
        searchResultsRef.set(r)
      }
  }

  def run(cursorIndex: Int): URIO[Env, Option[PreviewResult[Any]]] =
    for {
      results      <- searchResultsRef.get
      previewResult = results.previews.lift(cursorIndex)
      _            <- ZIO.foreach_(previewResult) { preview =>
                        // TODO: Log defects
                        preview.onRun.absorb.forkDaemon
                      }
    } yield previewResult
}

object HeadlessTerminal {
  def create: UManaged[HeadlessTerminal] =
    for {
      searchResultsRef <- Ref.makeManaged(SearchResults.empty[Any])
    } yield HeadlessTerminal(searchResultsRef)
}

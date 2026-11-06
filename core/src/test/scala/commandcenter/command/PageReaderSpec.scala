package commandcenter.command

import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import commandcenter.CommandBaseSpec
import fansi.Str
import zio.*
import zio.stream.ZStream
import zio.test.*

object PageReaderSpec extends CommandBaseSpec {

  def text(previews: Chunk[PreviewResult[?]]): Chunk[String] =
    previews.map(_.renderFn()).collect { case Rendered.Ansi(s) => s.plainText }

  // Emits 1 to 7 one element per pull, counting how many were read and noting when the stream is closed
  def countingStream(pulled: Ref[Int], closed: Ref[Boolean]): ZStream[Any, Nothing, PreviewResult[Unit]] =
    ZStream
      .range(1, 8, chunkSize = 1)
      .tap(_ => pulled.update(_ + 1))
      .map(i => PreviewResult.nothing(Rendered.Ansi(Str(i.toString))))
      .ensuring(closed.set(true))

  def pagedCommand(results: ZStream[Any, Nothing, PreviewResult[Unit]]): Command[Unit] =
    new Command[Unit] {
      val commandType: CommandType = CommandType.ExitCommand

      def commandNames: List[String] = List("paged")

      def title: String = "Paged"

      def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
        ZIO.succeed(PreviewResults.paginated(results, initialPageSize = 3, morePageSize = 3))
    }

  def spec: Spec[TestEnvironment & Env, Any] =
    suite("PageReaderSpec")(
      test("each page continues where the previous one stopped, reading every element once") {
        for {
          pulled <- Ref.make(0)
          closed <- Ref.make(false)
          pages  <- ZIO.scoped {
                     PageReader.make(countingStream(pulled, closed)).flatMap { reader =>
                       ZIO.collectAll(Chunk.fill(3)(reader.nextPage(3)))
                     }
                   }
          pulledCount <- pulled.get
          wasClosed   <- closed.get
        } yield assertTrue(
          pages.map { case (page, hasMore) => (text(page), hasMore) } == Chunk(
            (Chunk("1", "2", "3"), true),
            (Chunk("4", "5", "6"), true),
            (Chunk("7"), false)
          ),
          pulledCount == 7,
          wasClosed
        )
      },
      test("closing the scope closes the stream and ends the reader") {
        for {
          pulled    <- Ref.make(0)
          closed    <- Ref.make(false)
          reader    <- ZIO.scoped(PageReader.make(countingStream(pulled, closed)).tap(_.nextPage(2)))
          wasClosed <- closed.get
          after     <- reader.nextPage(2)
        } yield assertTrue(wasClosed, after == ((Chunk.empty, false)))
      },
      test("search results page through one open stream until released") {
        for {
          pulled  <- Ref.make(0)
          closed  <- Ref.make(false)
          results <- Command.search(
                       Vector(pagedCommand(countingStream(pulled, closed))),
                       Map.empty,
                       "paged",
                       defaultCommandContext
                     )
          more = results.previews.map(_.moreResults).collectFirst { case r: MoreResults.Remaining[?] => r }
          next        <- ZIO.foreach(more)(_.nextPage)
          pulledCount <- pulled.get
          openBefore  <- closed.get.map(!_)
          _           <- results.release
          closedAfter <- closed.get.repeatUntil(identity).timeout(5.seconds)
        } yield assertTrue(
          text(results.previews) == Chunk("1", "2", "3", "More..."),
          next.exists { case (page, rest) => text(page) == Chunk("4", "5", "6") && rest.isDefined },
          pulledCount == 7,
          openBefore,
          closedAfter.contains(true)
        )
      }
    )
}

package commandcenter.command

import com.typesafe.config.Config
import commandcenter.command.ChessCommand.ChessInterface
import commandcenter.config.Decoders.*
import commandcenter.event.KeyboardShortcut
import commandcenter.tools.Tools
import commandcenter.util.ProcessUtil
import commandcenter.CCRuntime.Env
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.LowerCamelcase
import sttp.model.internal.Rfc3986
import zio.*

import java.nio.file.Path
import scala.util.matching.Regex

final case class ChessCommand(
    title: String,
    override val commandNames: List[String],
    override val shortcuts: Set[KeyboardShortcut],
    chessInterface: ChessInterface,
    firefoxPath: Option[Path]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.ChessCommand

  def openChessAnalysis(text: String): Task[Unit] = {
    val url = chessInterface.url(chessInterface.parse(text))

    ProcessUtil.openBrowser(url, firefoxPath)
  }

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input         <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      clipboardText <- Tools.getClipboard.orElseSucceed("").map(_.trim)
    } yield PreviewResults.one(
      Preview.unit
        .onRun(
          openChessAnalysis(clipboardText)
        )
        .score(Scores.veryHigh(input.context))
    )
}

object ChessCommand extends CommandPlugin[ChessCommand] {

  val encodeQuery: String => String =
    Rfc3986.encode(Rfc3986.Query -- Set('&', '='), spaceAsPlus = false, encodePlus = true)

  val fenRegex: Regex = """(?i)([1-8PNBRQK]+/){7}[1-8PNBRQK]+( [WB])?( [KQ\-]+)?( [A-H1-8\-]+)?( \d+ \d+)?""".r

  val extractFenRegex: Regex = """[FEN "(.+?)"]""".r

  // TODO: Properly detect a PGN string. This is an extremely lazy "good enough" filter.
  def isPgn(text: String): Boolean =
    text.startsWith("1.") || text.contains("[Result")

  def extractFenFromPgn(pgnText: String): Option[String] =
    extractFenRegex.unapplySeq(pgnText).flatMap {
      case List(fen) => Some(fen)
      case _         => None
    }

  sealed trait ChessState

  object ChessState {
    final case class Fen(value: String) extends ChessState

    object Fen {

      // The initial starting state of all the pieces on the board
      val initialValue: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

      def parse(text: String): Option[Fen] =
        fenRegex
          .findFirstMatchIn(text)
          .map { m =>
            Fen(m.group(0))
          }
          .orElse(
            Pgn.parse(text).flatMap(p => extractFenFromPgn(p.value)).map(Fen(_))
          )
    }

    final case class Pgn(value: String) extends ChessState {

      lazy val tags: Map[String, String] =
        Pgn.tagRegex
          .findAllMatchIn(value)
          .map { m =>
            m.group(1) -> m.group(2).trim
          }
          .toMap

      def startedFromSetupPosition: Boolean =
        tags.get("SetUp").contains("1") && !tags.get("FEN").contains(Fen.initialValue)

      /** Tries to determine whether the player is from the white perspective or
        * not. This isn't necessarily an absolute thing, but we try to make the
        * best guess.
        */
      def isPlayerWhite: Boolean =
        // chesstempo.com (and others) marks you as "player" for their puzzles.
        !tags.get("Black").exists(_.equalsIgnoreCase("player"))

      def isPlayerBlack: Boolean = !isPlayerWhite

      def moves: String =
        value.linesIterator.toSeq
          .map(_.trim)
          .filterNot(s => s.isEmpty || s.startsWith("["))
          .mkString("\n")
    }

    object Pgn {

      val tagRegex: Regex = """\[(\w+)\s+"(.+?)"\]""".r

      def parse(text: String): Option[Pgn] =
        Option.when(isPgn(text))(Pgn(text))
    }
  }

  sealed trait ChessInterface extends EnumEntry with LowerCamelcase {

    def parse(text: String): Option[ChessState] =
      ChessState.Pgn.parse(text).orElse(ChessState.Fen.parse(text))

    def url(state: Option[ChessState]): String
  }

  object ChessInterface extends Enum[ChessInterface] with CirceEnum[ChessInterface] {

    case object Default extends ChessInterface {

      def url(state: Option[ChessState]): String =
        state match {
          case Some(p @ ChessState.Pgn(pgn)) =>
            val encodedQuery = encodeQuery(pgn)
            s"https://www.chess.com/analysis?flip=${p.isPlayerBlack}&pgn=$encodedQuery"

          case Some(ChessState.Fen(fen)) =>
            val encodedQuery = fen.replace(' ', '_')
            s"https://lichess.org/analysis/$encodedQuery"

          case None =>
            "https://lichess.org/analysis"
        }
    }

    case object ChessCom extends ChessInterface {

      def url(state: Option[ChessState]): String =
        state match {
          case Some(p @ ChessState.Pgn(pgn)) =>
            val encodedQuery = encodeQuery(pgn)
            s"https://www.chess.com/analysis?flip=${p.isPlayerBlack}&pgn=$encodedQuery"

          case Some(ChessState.Fen(fen)) =>
            s"https://www.chess.com/analysis?flip=false&fen=$fen"

          case None =>
            "https://www.chess.com/analysis"
        }
    }

    case object Lichess extends ChessInterface {

      override def parse(text: String): Option[ChessState] =
        // Lichess doesn't support sharable links of PGNs that contain a FEN as its initial state. It only supports
        // PGNs from the very first move, which is why we filter these types of PGNs out.
        ChessState.Pgn
          .parse(text)
          .filter(!_.startedFromSetupPosition)
          .orElse(ChessState.Fen.parse(text))

      def url(state: Option[ChessState]): String =
        state match {
          case Some(ChessState.Fen(fen)) =>
            val encodedQuery = fen.replace(' ', '_')
            s"https://lichess.org/analysis/$encodedQuery"

          case Some(pgn @ ChessState.Pgn(_)) if !pgn.startedFromSetupPosition =>
            val encodedQuery = encodeQuery(pgn.moves)
            val color = if (pgn.isPlayerBlack) "black" else "white"
            s"https://lichess.org/analysis/pgn/$encodedQuery?color=$color"

          case Some(ChessState.Pgn(_)) | None =>
            "https://lichess.org/analysis"
        }
    }

    lazy val values: IndexedSeq[ChessInterface] = findValues
  }

  def make(config: Config): IO[CommandPluginError, ChessCommand] =
    for {
      title          <- config.getZIO[Option[String]]("title")
      commandNames   <- config.getZIO[Option[List[String]]]("commandNames")
      shortcuts      <- config.getZIO[Option[Set[KeyboardShortcut]]]("shortcuts")
      chessInterface <- config.getZIO[Option[ChessInterface]]("chessInterface")
      firefoxPath    <- config.getZIO[Option[Path]]("firefoxPath")
    } yield ChessCommand(
      title.getOrElse("Chess Analysis"),
      commandNames.getOrElse(Nil),
      shortcuts.getOrElse(Set.empty),
      chessInterface.getOrElse(ChessInterface.Default),
      firefoxPath
    )
}

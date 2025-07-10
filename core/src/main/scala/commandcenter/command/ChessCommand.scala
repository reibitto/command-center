package commandcenter.command

import com.typesafe.config.Config
import commandcenter.config.Decoders.*
import commandcenter.event.KeyboardShortcut
import commandcenter.tools.Tools
import commandcenter.util.ProcessUtil
import commandcenter.CCRuntime.Env
import sttp.model.internal.Rfc3986
import zio.*

import java.nio.file.Path

final case class ChessCommand(
    title: String,
    override val commandNames: List[String],
    override val shortcuts: Set[KeyboardShortcut],
    firefoxPath: Option[Path]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.ChessCommand

  val encodeQuery: String => String =
    Rfc3986.encode(Rfc3986.Query -- Set('&', '='), spaceAsPlus = false, encodePlus = true)

  // TODO: Properly detect a PGN string. This is an extremely lazy "good enough" filter.
  def isPgn(text: String): Boolean =
    text.contains("[Result")

  def openBrowser(text: String): Task[Unit] = {
    val url = if (isPgn(text)) {
      val encodedQuery = encodeQuery(text)
      s"https://www.chess.com/analysis?flip=false&pgn=$encodedQuery"
    } else {
      "https://www.chess.com/analysis?flip=false"
    }

    ProcessUtil.openBrowser(url, firefoxPath)
  }

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      pgn   <- Tools.getClipboard.orElseSucceed("")
    } yield PreviewResults.one(
      Preview.unit
        .onRun(
          openBrowser(pgn)
        )
        .score(Scores.veryHigh(input.context))
    )
}

object ChessCommand extends CommandPlugin[ChessCommand] {

  def make(config: Config): IO[CommandPluginError, ChessCommand] =
    for {
      title        <- config.getZIO[Option[String]]("title")
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
      shortcuts    <- config.getZIO[Option[Set[KeyboardShortcut]]]("shortcuts")
      firefoxPath  <- config.getZIO[Option[Path]]("firefoxPath")
    } yield ChessCommand(
      title.getOrElse("Chess Analysis"),
      commandNames.getOrElse(Nil),
      shortcuts.getOrElse(Set.empty),
      firefoxPath
    )
}

package commandcenter.ject

import com.typesafe.config.Config
import commandcenter.command.*
import commandcenter.config.Decoders.*
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import fansi.{Color, Str}
import ject.ja.docs.KanjiDoc
import ject.ja.lucene.KanjiReader
import zio.{Scope, ZIO}

import java.nio.file.Path

final case class KanjiCommand(
    commandNames: List[String],
    luceneIndex: KanjiReader,
    quickPrefixes: List[String],
    showScore: Boolean
) extends Command[Unit] {
  val commandType: CommandType = CommandType.External(getClass.getCanonicalName)
  val title: String = "Kanji"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <-
        ZIO
          .fromOption(
            searchInput
              .asPrefixedQuick(quickPrefixes*)
              .orElse(searchInput.asPrefixed)
              .filter(_.rest.nonEmpty)
              .map(_.rest)
          )
          .orElseFail(CommandError.NotApplicable)
      kanjiStream = luceneIndex.searchByParts(input).mapError(CommandError.UnexpectedError(this))
    } yield PreviewResults.paginated(
      kanjiStream.map { kanji =>
        Preview.unit
          .score(Scores.veryHigh(searchInput.context) * 1.5)
          .onRun(Tools.setClipboard(kanji.doc.kanji))
          .renderedAnsi(render(kanji.doc, kanji.score))
      },
      initialPageSize = 10,
      morePageSize = 30
    )

  // TODO: Consider creating a StrBuilder class to make this nicer
  def render(k: KanjiDoc, score: Double): Str =
    (if (k.isJouyouKanji) Str(" ") else Color.Red("×")) ++
      Color.Green(k.kanji) ++ (if (k.kunYomi.isEmpty) "" else " ") ++ k.kunYomi.map { ku =>
        Color.Magenta(ku)
      }.reduceOption(_ ++ " " ++ _).getOrElse(Str("")) ++ " " ++ k.onYomi.map { o =>
        Color.Cyan(o)
      }.reduceOption(_ ++ " " ++ _).getOrElse(Str("")) ++ " " ++ k.meaning.mkString("; ") ++
      (if (showScore) Color.DarkGray(" %1.2f".format(score)) else "")

}

object KanjiCommand extends CommandPlugin[KanjiCommand] {

  def make(config: Config): ZIO[Scope, CommandPluginError, KanjiCommand] =
    // TODO: Ensure index exists. If not, create it here (put data in .command-center folder)
    for {
      commandNames   <- config.getZIO[Option[List[String]]]("commandNames")
      dictionaryPath <- config.getZIO[Path]("dictionaryPath")
      luceneIndex    <- KanjiReader.make(dictionaryPath).mapError(CommandPluginError.UnexpectedException.apply)
      showScore      <- config.getZIO[Option[Boolean]]("showScore")
      quickPrefixes  <- config.getZIO[Option[List[String]]]("quickPrefixes")
    } yield KanjiCommand(
      commandNames.getOrElse(List("kanji", "k")),
      luceneIndex,
      quickPrefixes.getOrElse(Nil),
      showScore.getOrElse(false)
    )
}

package commandcenter.ject

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.*
import commandcenter.config.Decoders.pathDecoder
import commandcenter.tools.Tools
import ject.ja.docs.KanjiDoc
import ject.ja.lucene.KanjiReader
import zio.ZIO
import zio.managed.*

import java.nio.file.Path

final case class KanjiCommand(
  commandNames: List[String],
  luceneIndex: KanjiReader,
  quickPrefixes: List[String],
  showScore: Boolean
) extends Command[Unit] {
  val commandType: CommandType = CommandType.External(getClass.getCanonicalName)
  val title: String            = "Kanji"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input      <-
        ZIO
          .fromOption(
            searchInput
              .asPrefixedQuick(quickPrefixes: _*)
              .orElse(searchInput.asPrefixed)
              .filter(_.rest.nonEmpty)
              .map(_.rest)
          )
          .orElseFail(CommandError.NotApplicable)
      kanjiStream = luceneIndex.searchByParts(input).mapError(CommandError.UnexpectedException)
    } yield PreviewResults.paginated(
      kanjiStream.map { kanji =>
        Preview.unit
          .score(Scores.high(searchInput.context) * 1.5)
          .onRun(Tools.setClipboard(kanji.doc.kanji))
          .renderedAnsi(render(kanji.doc, kanji.score))
      },
      pageSize = 20
    )

  // TODO: Consider creating a StrBuilder class to make this nicer
  def render(k: KanjiDoc, score: Double): fansi.Str =
    (if (k.isJouyouKanji) fansi.Str("　") else fansi.Color.Red("×")) ++
      fansi.Color.Green(k.kanji) ++ (if (k.kunYomi.isEmpty) ""
                                     else "　") ++ k.kunYomi.map { ku =>
        fansi.Color.Magenta(ku)
      }.reduceOption(_ ++ "　" ++ _).getOrElse(fansi.Str("")) ++ "　" ++ k.onYomi.map { o =>
        fansi.Color.Cyan(o)
      }.reduceOption(_ ++ "　" ++ _).getOrElse(fansi.Str("")) ++ " " ++ k.meaning.mkString("; ") ++
      (if (showScore) fansi.Color.DarkGray(" %1.2f".format(score)) else "")

}

object KanjiCommand extends CommandPlugin[KanjiCommand] {
  def make(config: Config): Managed[CommandPluginError, KanjiCommand] =
    ???
    // TODO: Ensure index exists. If not, create it here (put data in .command-center folder)
//    for {
//      commandNames   <- config.getManaged[Option[List[String]]]("commandNames")
//      dictionaryPath <- config.getManaged[Path]("dictionaryPath")
//      luceneIndex    <- KanjiReader.make(dictionaryPath.resolve("kanji")).mapError(CommandPluginError.UnexpectedException)
//      showScore      <- config.getManaged[Option[Boolean]]("showScore")
//      quickPrefixes  <- config.getManaged[Option[List[String]]]("quickPrefixes")
//    } yield KanjiCommand(
//      commandNames.getOrElse(List("kanji", "k")),
//      luceneIndex,
//      quickPrefixes.getOrElse(Nil),
//      showScore.getOrElse(false)
//    )
}

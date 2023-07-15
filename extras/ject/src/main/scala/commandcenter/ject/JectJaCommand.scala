package commandcenter.ject

import com.typesafe.config.Config
import commandcenter.command.*
import commandcenter.config.Decoders.*
import commandcenter.locale.JapaneseText
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import ject.ja.docs.WordDoc
import ject.ja.lucene.WordReader
import ject.SearchPattern
import zio.{Scope, ZIO}

import java.nio.file.Path

final case class JectJaCommand(commandNames: List[String], luceneIndex: WordReader, showScore: Boolean)
    extends Command[Unit] {
  val commandType: CommandType = CommandType.External(getClass.getCanonicalName)
  val title: String = "Ject (ja)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO
                 .fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty).map(_.rest))
                 .orElseFail(CommandError.NotApplicable)
                 .orElse {
                   if (searchInput.input.exists(JapaneseText.isJapanese))
                     ZIO.succeed(searchInput.input)
                   else
                     ZIO.fail(CommandError.NotApplicable)
                 }
      searchPattern = SearchPattern(input)
      wordStream = luceneIndex.search(searchPattern).mapError(CommandError.UnexpectedError(this))
    } yield PreviewResults.paginated(
      wordStream.map { word =>
        Preview.unit
          .score(Scores.high(searchInput.context))
          .onRun(Tools.setClipboard(input))
          .renderedAnsi(renderWord(word.doc, word.score))
      },
      pageSize = 10
    )

  def renderWord(word: WordDoc, score: Double): fansi.Str = {
    val kanjiTerms = (word.kanjiTerms.headOption.map { k =>
      fansi.Color.Green(k)
    }.toList ++ word.kanjiTerms.drop(1).map { k =>
      fansi.Color.LightGreen(k)
    }).reduceOption(_ ++ " " ++ _).getOrElse(fansi.Str(""))

    val readingTerms = (word.readingTerms.headOption.map { k =>
      fansi.Color.Blue(k)
    }.toList ++ word.readingTerms.drop(1).map { k =>
      fansi.Color.LightBlue(k)
    }).reduceOption(_ ++ " " ++ _).getOrElse(fansi.Str(""))

    val definitions = word.definitions.zipWithIndex.map { case (d, i) =>
      fansi.Color.LightGray((i + 1).toString) ++ " " ++ d
    }.reduceOption(_ ++ "\n" ++ _).getOrElse(fansi.Str(""))

    val partsOfSpeech = word.partsOfSpeech.map { pos =>
      fansi.Back.DarkGray(pos)
    }.reduceOption(_ ++ " " ++ _).getOrElse(fansi.Str(""))

    // TODO: Consider creating a StrBuilder class to make this nicer
    (if (kanjiTerms.length == 0) fansi.Str("") else kanjiTerms ++ " ") ++
      (if (readingTerms.length == 0) fansi.Str("") else readingTerms ++ " ") ++
      partsOfSpeech ++ (if (showScore) fansi.Color.DarkGray(" %1.2f".format(score)) else "") ++ "\n" ++
      definitions
  }

}

object JectJaCommand extends CommandPlugin[JectJaCommand] {

  def make(config: Config): ZIO[Scope, CommandPluginError, JectJaCommand] =
    // TODO: Ensure index exists. If not, create it here (put data in .command-center folder)
    for {
      commandNames   <- config.getZIO[Option[List[String]]]("commandNames")
      dictionaryPath <- config.getZIO[Path]("dictionaryPath")
      luceneIndex    <- WordReader.make(dictionaryPath).mapError(CommandPluginError.UnexpectedException)
      showScore      <- config.getZIO[Option[Boolean]]("showScore")
    } yield JectJaCommand(commandNames.getOrElse(List("ject", "j")), luceneIndex, showScore.getOrElse(false))
}

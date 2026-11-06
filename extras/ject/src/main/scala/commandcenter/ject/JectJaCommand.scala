package commandcenter.ject

import com.typesafe.config.Config
import commandcenter.command.*
import commandcenter.config.Decoders.*
import commandcenter.locale.JapaneseText
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import fansi.{Back, Color, Str}
import ject.ja.docs.WordDoc
import ject.ja.lucene.WordReader
import ject.SearchPattern
import zio.*

import java.nio.file.Path

final case class JectJaCommand(commandNames: List[String], luceneIndex: WordReader, showScore: Boolean)
    extends Command[Unit] {
  val commandType: CommandType = CommandType.External.of(getClass)
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
        val targetWord = word.doc.kanjiTerms.headOption
          .orElse(word.doc.readingTerms.headOption)
          .getOrElse(input)

        Preview.unit
          .score(Scores.high(searchInput.context))
          .onRun(Tools.setClipboard(targetWord))
          .renderedAnsi(renderWord(word.doc, word.score))
      },
      initialPageSize = 10,
      morePageSize = 100
    )

  def renderWord(word: WordDoc, score: Double): Str = {
    val kanjiTerms =
      Str.join(word.kanjiTerms.headOption.map(Color.Green(_)) ++ word.kanjiTerms.drop(1).map(Color.LightGreen(_)), " ")

    val readingTerms =
      Str.join(
        word.readingTerms.headOption.map(Color.Blue(_)) ++ word.readingTerms.drop(1).map(Color.LightBlue(_)),
        " "
      )

    val definitions = word.definitions match {
      case Seq(d) => Str(d)

      case definitions =>
        Str.join(definitions.zipWithIndex.map { case (d, i) => Color.LightGray((i + 1).toString) ++ " " ++ d }, "\n")
    }

    val partsOfSpeech = Str.join(word.partsOfSpeech.map(Back.DarkGray(_)), " ")

    // TODO: Consider creating a StrBuilder class to make this nicer
    (if (kanjiTerms.length == 0) Str("") else kanjiTerms ++ " ") ++
      (if (readingTerms.length == 0) Str("") else readingTerms ++ " ") ++
      partsOfSpeech ++ (if (showScore) Color.DarkGray(" %1.2f".format(score)) else "") ++ "\n" ++
      definitions
  }

}

object JectJaCommand extends CommandPlugin[JectJaCommand] {

  def make(config: Config): ZIO[Scope, CommandPluginError, JectJaCommand] =
    // TODO: Ensure index exists. If not, create it here (put data in .command-center folder)
    for {
      commandNames   <- commandNames(config, "ject", "j")
      dictionaryPath <- config.getZIO[Path]("dictionaryPath")
      luceneIndex    <- WordReader.make(dictionaryPath).mapError(CommandPluginError.UnexpectedException.apply)
      showScore      <- config.getZIO("showScore", false)
    } yield JectJaCommand(commandNames, luceneIndex, showScore)
}

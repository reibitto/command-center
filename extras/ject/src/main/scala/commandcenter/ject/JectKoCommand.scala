package commandcenter.ject

import com.typesafe.config.Config
import commandcenter.command.*
import commandcenter.config.Decoders.*
import commandcenter.locale.KoreanText
import commandcenter.tools.Tools
import commandcenter.CCRuntime.Env
import fansi.{Color, Str}
import ject.ko.docs.WordDoc
import ject.ko.lucene.WordReader
import ject.SearchPattern
import zio.*

import java.nio.file.Path

final case class JectKoCommand(commandNames: List[String], luceneIndex: WordReader, showScore: Boolean)
    extends Command[Unit] {
  val commandType: CommandType = CommandType.External.of(getClass)
  val title: String = "Ject (ko)"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO
                 .fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty).map(_.rest))
                 .orElseFail(CommandError.NotApplicable)
                 .orElse {
                   if (searchInput.input.exists(KoreanText.isKorean))
                     ZIO.succeed(searchInput.input)
                   else
                     ZIO.fail(CommandError.NotApplicable)
                 }
      searchPattern = SearchPattern(input)
      wordStream = luceneIndex.search(searchPattern).mapError(CommandError.UnexpectedError(this))
    } yield PreviewResults.paginated(
      wordStream.map { word =>
        val targetWord = word.doc.hangulTerms.headOption
          .orElse(word.doc.hanjaTerms.headOption)
          .getOrElse(input)

        Preview.unit
          .score(Scores.high(searchInput.context))
          .onRun(Tools.setClipboard(targetWord))
          .renderedAnsi(renderWord(word.doc, word.score))
      },
      initialPageSize = 10,
      morePageSize = 50
    )

  def renderWord(word: WordDoc, score: Double): Str = {
    // Only do this hacky rendering method for krdict* dictionaries
    val definitionLines = word.definitions.headOption.getOrElse("").linesIterator.toSeq

    val term = Color.Blue(definitionLines.head)
    val pronunciation = Color.Green(word.pronunciation.mkString(" "))

    val line1 =
      if (word.pronunciation.isEmpty)
        term.toString
      else
        s"$term $pronunciation"

    val lineN = definitionLines.tail.mkString("\n")

    s"$line1\n$lineN"
  }

}

object JectKoCommand extends CommandPlugin[JectKoCommand] {

  def make(config: Config): ZIO[Scope, CommandPluginError, JectKoCommand] =
    // TODO: Ensure index exists. If not, create it here (put data in .command-center folder)
    for {
      commandNames   <- config.getZIO[Option[List[String]]]("commandNames")
      dictionaryPath <- config.getZIO[Path]("dictionaryPath")
      luceneIndex    <- WordReader.make(dictionaryPath).mapError(CommandPluginError.UnexpectedException.apply)
      showScore      <- config.getZIO[Option[Boolean]]("showScore")
    } yield JectKoCommand(commandNames.getOrElse(List("ject", "j")), luceneIndex, showScore.getOrElse(false))
}

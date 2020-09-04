package commandcenter.ject

import java.nio.file.Path

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command._
import commandcenter.config.Decoders.pathDecoder
import commandcenter.locale.JapaneseText
import commandcenter.tools
import ject.SearchPattern
import ject.entity.WordDocument
import ject.lucene.WordIndex
import zio.{ TaskManaged, UIO, ZIO, ZManaged }

final case class JectCommand(commandNames: List[String], dictionaryPath: Path) extends Command[Unit] {
  val commandType: CommandType = CommandType.External(getClass.getCanonicalName)
  val title: String            = "Ject"
  val luceneIndex: WordIndex   = new WordIndex(dictionaryPath.resolve("word"))

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input        <- ZIO
                        .fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty).map(_.rest))
                        .orElseFail(CommandError.NotApplicable)
                        .orElse {
                          if (searchInput.input.exists(JapaneseText.isJapanese))
                            UIO(searchInput.input)
                          else
                            ZIO.fail(CommandError.NotApplicable)
                        }
      searchPattern = SearchPattern(input)
      words        <- luceneIndex.search(searchPattern).mapError(CommandError.UnexpectedException)
    } yield words.toList.map { word =>
      Preview.unit
        .score(Scores.high(searchInput.context))
        .onRun(tools.setClipboard(input))
        .view(renderWord(word))
    }

  def renderWord(word: WordDocument): fansi.Str = {
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

    val definitions = word.definitions.zipWithIndex.map {
      case (d, i) =>
        fansi.Color.LightGray((i + 1).toString) ++ " " ++ d
    }.reduceOption(_ ++ "\n" ++ _).getOrElse(fansi.Str(""))

    val partsOfSpeech = word.partsOfSpeech.map { pos =>
      fansi.Back.DarkGray(pos)
    }.reduceOption(_ ++ " " ++ _).getOrElse(fansi.Str(""))

    // TODO: Consider creating a StrBuilder class to make this nicer
    (if (kanjiTerms.length == 0) fansi.Str("") else kanjiTerms ++ " ") ++
      (if (readingTerms.length == 0) fansi.Str("") else readingTerms ++ " ") ++
      partsOfSpeech ++ "\n" ++ definitions
  }

}

object JectCommand extends CommandPlugin[JectCommand] {
  def make(config: Config): TaskManaged[JectCommand] =
    // TODO: Ensure index exists. If not, create it here (put data in .command-center folder)
    ZManaged.fromEither(
      for {
        commandNames   <- config.get[Option[List[String]]]("commandNames")
        dictionaryPath <- config.get[Path]("dictionaryPath")
      } yield JectCommand(commandNames.getOrElse(List("ject", "j")), dictionaryPath)
    )
}

package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.SnippetsCommand.Snippet
import commandcenter.scorers.LengthScorer
import commandcenter.tools
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ TaskManaged, ZIO, ZManaged }

final case class SnippetsCommand(commandNames: List[String], snippets: List[Snippet]) extends Command[String] {
  val commandType: CommandType = CommandType.SnippetsCommand
  val title: String            = "Snippets"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      (isPrefixed, snippetSearch) <-
        ZIO.fromOption(searchInput.asPrefixed).bimap(_ => (false, searchInput.input), s => (true, s.rest)).merge

    } yield snippets.map { snip =>
      val score =
        if (isPrefixed)
          Scores.high(searchInput.context)
        else {
          // TODO: Implement better scoring algorithm (like Sublime Text fuzzy search)
          val matchScore = LengthScorer.score(snip.keyword, snippetSearch)
          Scores.high(searchInput.context) * matchScore
        }

      (snip, score)
    }.collect {
      case (snippet, score) if score > 0 =>
        Preview(snippet.value)
          .onRun(tools.setClipboard(snippet.value))
          .score(score)
          .view(DefaultView(title, fansi.Color.Magenta(snippet.keyword) ++ " " ++ snippet.value))
    }
}

object SnippetsCommand extends CommandPlugin[SnippetsCommand] {
  final case class Snippet(keyword: String, value: String)

  object Snippet {
    implicit val decoder: Decoder[Snippet] = Decoder.forProduct2("keyword", "value")(Snippet.apply)
  }

  def make(config: Config): TaskManaged[SnippetsCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
        snippets     <- config.get[Option[List[Snippet]]]("snippets")
      } yield SnippetsCommand(
        commandNames.getOrElse(List("snippets", "snippet", "snip")),
        snippets.getOrElse(List.empty)
      )
    )
}

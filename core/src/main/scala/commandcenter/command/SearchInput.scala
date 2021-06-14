package commandcenter.command

import commandcenter.CommandContext
import commandcenter.scorers.LengthScorer

import scala.collection.mutable.ArrayBuffer

final case class SearchInput(
  input: String,
  aliasedInputs: List[String],
  commandNames: List[String],
  context: CommandContext
) {

  /**
   * Parses the search input into tokenized arguments. For example, "myCommand -a 1 "some subcommand" will be parsed into
   * ["-a", "1", "some subcommand"]. Note that the command name is a separate field and isn't considered an argument.
   *
   * Returns Some if the user input matches the command name (also taking into account custom aliases), otherwise None.
   */
  def asArgs: Option[CommandInput.Args] =
    // TODO: Optimize this. Possibly with collectFirst + an extractor
    (input :: aliasedInputs).flatMap { input =>
      val (commandName, rest) = input.split("[ ]+", 2) match {
        case Array(prefix, rest) => (prefix, s" $rest")
        case Array(prefix)       => (prefix, "")
      }

      if (commandNames.exists(_.equalsIgnoreCase(commandName)))
        Some(CommandInput.Args(commandName, SearchInput.tokenizeArgs(rest), context))
      else
        None
    }.headOption

  /**
   * Parses the search input into 2 tokens: a matching prefix and the rest of the input. For example,
   * "myCommand one two three" will be parsed into ["myCommand", "one two three"].
   *
   * Returns Some if the user input matches the prefix (also taking into account custom aliases), otherwise None.
   */
  def asPrefixed: Option[CommandInput.Prefixed] =
    // TODO: Optimize this. Possibly with collectFirst + an extractor
    (input :: aliasedInputs).flatMap { input =>
      val (prefix, rest) = input.split("\\p{javaWhitespace}+", 2) match {
        case Array(prefix, rest) => (prefix, s"$rest")
        case Array(prefix)       => (prefix, "")
      }

      commandNames.map { name =>
        LengthScorer.scorePrefix(name, prefix)
      }.maxOption.collect {
        case score if score > 0 => CommandInput.Prefixed(prefix, rest, context.matchScore(score))
      }
    }.headOption

  def asPrefixedNoSpace(prefixes: String*): Option[CommandInput.Prefixed] =
    prefixes.collectFirst {
      case prefix if input.startsWith(prefix) =>
        val rest = input.substring(prefix.length)

        CommandInput.Prefixed(prefix, rest, context)
    }

  /**
   * Parses the search input as 1 keyword. This is useful for commands that don't take in arguments, such as "exit".
   * Prefixes are also matched, but with a lower score. For example, if the command is "exit" and the user types "ex",
   * this will match but with a lower score than if the user typed "exi" or "exit".
   *
   * Returns Some if the user input matches the keyword (also taking into account custom aliases), otherwise None.
   */
  def asKeyword: Option[CommandInput.Keyword] =
    scoreInput(input).collect {
      case score if score > 0 => CommandInput.Keyword(input, context.matchScore(score))
    }

  private def scoreInput(text: String): Option[Double] =
    aliasedInputs.flatMap { aliasedInput =>
      commandNames.map { commandName =>
        val matchScore      = LengthScorer.scoreDefault(commandName, text)
        val aliasMatchScore = LengthScorer.scoreDefault(commandName, aliasedInput)
        matchScore max aliasMatchScore
      }
    }.maxOption
}

object SearchInput {
  // TODO: This is a naive algorithm that probably doesn't handle all the same cases that something like Bash does. It
  // needs to be improved eventually. For now it just handles tokenizing on spaces while also considering everything
  // in quotes a single token.
  def tokenizeArgs(input: String): List[String] = {
    val tokens = ArrayBuffer[String]()
    var i      = 0
    var start  = -1
    var quote  = false

    while (i < input.length) {
      val c = input.charAt(i)

      if (c == ' ' && start >= 0 && !quote) {
        tokens += input.substring(start, i)
        start = -1
      } else if (c == '"')
        if (quote) {
          tokens += input.substring(start, i)
          quote = false
          start = -1
        } else {
          start = i + 1
          quote = true
        }
      else if (start < 0 && c != ' ')
        start = i

      i += 1
    }

    if (start != -1)
      tokens += input.substring(start)

    tokens.toList
  }
}

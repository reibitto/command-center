package commandcenter.command

import commandcenter.scorers.LengthScorer
import commandcenter.util.StringExtensions.StringExtension
import commandcenter.CommandContext

final case class SearchInput(
    input: String,
    aliasedInputs: List[String],
    commandNames: List[String],
    context: CommandContext
) {

  /** Parses the search input into tokenized arguments. For example, "myCommand
    * -a 1 "some subcommand" will be parsed as ["-a", "1", "some subcommand"].
    * Note that the command name is a separate field and isn't considered an
    * argument.
    *
    * Returns Some if the user input matches the command name (also taking into
    * account custom aliases), otherwise None.
    */
  def asArgs: Option[CommandInput.Args] =
    (input :: aliasedInputs).distinct.flatMap { input =>
      val (commandName, rest) = SearchInput.splitCommandAndRest(input)

      if (commandNames.exists(_.equalsIgnoreCase(commandName)))
        Some(CommandInput.Args(commandName, SearchInput.tokenizeArgs(rest), context))
      else
        None
    }.headOption

  /** Parses the search input into 2 tokens: a matching prefix and the rest of
    * the input. For example, "myCommand one two three" will be parsed as
    * ["myCommand", "one two three"].
    *
    * Returns Some if the user input matches the prefix (also taking into
    * account custom aliases), otherwise None.
    */
  def asPrefixed: Option[CommandInput.Prefixed] =
    (input :: aliasedInputs).distinct.flatMap { input =>
      val (prefix, rest) = SearchInput.splitOnWhitespace(input)

      commandNames.map { name =>
        LengthScorer.scorePrefix(name, prefix)
      }.maxOption.collect {
        case score if score > 0 => CommandInput.Prefixed(prefix, rest, context.matchScore(score))
      }
    }.headOption

  /** Parses the search input into 2 tokens: a matching prefix and the rest of
    * the input (separated by no space). For example, "!one two three" will be
    * parsed as ["!", "one two three"].
    *
    * Returns Some if the user input matches one of prefixes (also taking into
    * account custom aliases), otherwise None.
    */
  def asPrefixedQuick(prefixes: String*): Option[CommandInput.Prefixed] =
    prefixes.collectFirst {
      case prefix if input.startsWithIgnoreCase(prefix) =>
        val rest = input.substring(prefix.length)

        CommandInput.Prefixed(prefix, rest, context)
    }

  /** Parses the search input as 1 keyword. This is useful for commands that
    * don't take in arguments, such as "exit". Prefixes are also matched, but
    * with a lower score. For example, if the command is "exit" and the user
    * types "ex", this will match but with a lower score than if the user typed
    * "exi" or "exit".
    *
    * Returns Some if the user input matches the keyword (also taking into
    * account custom aliases), otherwise None.
    */
  def asKeyword: Option[CommandInput.Keyword] =
    scoreInput(input).collect {
      case score if score > 0 => CommandInput.Keyword(input, context.matchScore(score))
    }

  private def scoreInput(text: String): Option[Double] =
    aliasedInputs.flatMap { aliasedInput =>
      commandNames.map { commandName =>
        val matchScore = LengthScorer.scoreDefault(commandName, text)
        val aliasMatchScore = LengthScorer.scoreDefault(commandName, aliasedInput)
        matchScore max aliasMatchScore
      }
    }.maxOption
}

object SearchInput {

  /** Splits text on its first run of one-or-more ASCII spaces into a
    * (commandPart, rest) pair, without paying for regex compilation the way
    * `input.split("[ ]+", 2)` would on every call.
    */
  private[command] def splitCommandAndRest(input: String): (String, String) = {
    val spaceIndex = input.indexOf(' ')

    if (spaceIndex < 0) (input, "")
    else {
      var restStart = spaceIndex
      while (restStart < input.length && input.charAt(restStart) == ' ')
        restStart += 1

      (input.substring(0, spaceIndex), " " + input.substring(restStart))
    }
  }

  /** Same idea as [[splitCommandAndRest]], but for general (not just ASCII
    * space) whitespace (regex-free equivalent of
    * `input.split("\\p{javaWhitespace}+", 2)`).
    */
  private[command] def splitOnWhitespace(input: String): (String, String) = {
    val n = input.length
    var i = 0
    while (i < n && !Character.isWhitespace(input.charAt(i)))
      i += 1

    if (i >= n) (input, "")
    else {
      var restStart = i
      while (restStart < n && Character.isWhitespace(input.charAt(restStart)))
        restStart += 1

      (input.substring(0, i), input.substring(restStart))
    }
  }

  /** Splits a full line of user input into separate command statements,
    * mirroring nushell semantics (`;` is the statement separator).
    *
    * For example, `md5 a; md5 b` is split into `List("md5 a", "md5 b")`, while
    * `echo "a;b"` is left as a single statement since the semicolon is inside
    * quotes.
    */
  def splitStatements(input: String): List[String] = {
    val statements = List.newBuilder[String]
    val current = new StringBuilder
    var quoteChar: Char = 0
    var i = 0
    val n = input.length

    while (i < n) {
      val c = input.charAt(i)

      if (quoteChar != 0)
        if (quoteChar == '"' && c == '\\' && i + 1 < n) {
          current.append(c).append(input.charAt(i + 1))
          i += 2
        } else {
          current.append(c)
          if (c == quoteChar)
            quoteChar = 0
          i += 1
        }
      else if (c == '"' || c == '\'') {
        quoteChar = c
        current.append(c)
        i += 1
      } else if (c == ';') {
        statements += current.toString
        current.clear()
        i += 1
      } else {
        current.append(c)
        i += 1
      }
    }

    statements += current.toString

    statements.result().map(_.trim).filter(_.nonEmpty)
  }

  /** Tokenizes a string of arguments, honoring quoting so that whitespace
    * inside a quoted span doesn't split it into multiple tokens. Both double
    * quotes and single quotes are supported.
    *
    * For example, `-a 1 "some subcommand"` is tokenized as
    * `List("-a", "1", "some subcommand")`.
    */
  def tokenizeArgs(input: String): List[String] = {
    val tokens = List.newBuilder[String]
    val current = new StringBuilder
    var inToken = false
    var i = 0
    val n = input.length

    def flush(): Unit =
      if (inToken) {
        tokens += current.toString
        current.clear()
        inToken = false
      }

    while (i < n) {
      val c = input.charAt(i)

      if (Character.isWhitespace(c)) {
        flush()
        i += 1
      } else if (c == '"' || c == '\'') {
        val quoteChar = c
        inToken = true
        i += 1

        var closed = false
        while (i < n && !closed) {
          val qc = input.charAt(i)

          if (qc == quoteChar) {
            closed = true
            i += 1
          } else if (quoteChar == '"' && qc == '\\' && i + 1 < n) {
            current.append(input.charAt(i + 1))
            i += 2
          } else {
            current.append(qc)
            i += 1
          }
        }
      } else {
        inToken = true
        current.append(c)
        i += 1
      }
    }

    flush()

    tokens.result()
  }
}

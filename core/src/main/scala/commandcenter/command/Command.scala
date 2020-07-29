package commandcenter.command

import java.util.Locale

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.CommandError._
import commandcenter.locale.Language
import commandcenter.util.OS
import commandcenter.view.syntax._
import commandcenter.view.{ DefaultView, ViewInstances }
import commandcenter.{ CCTerminal, CommandContext }
import io.circe
import zio._

import scala.collection.mutable.ArrayBuffer
import scala.util.Try

trait Command[+R] extends ViewInstances {
  val commandType: CommandType

  def commandNames: List[String]

  // TODO: LocalizedString
  def title: String

  def locales: Set[Locale] = Set.empty

  def supportedOS: Set[OS] = Set.empty

  /**
   * This event is fired whenever the input text changes (debounced). Override this method when you're writing a
   * command that has to respond to arbitrary user input (such as a calculator command: `1 + 1` and so on).
   * */
  def inputPreview(input: String, context: CommandContext): ZIO[Env, CommandError, List[PreviewResult[R]]] =
    IO.fail(NotApplicable)

  /**
   * This event is fired whenever the input text changes (debounced) and the input text is exactly equal to or starts
   * with the command name. Override this method when you're writing commands that are triggered with a keyword and
   * take in no arguments. Examples include: `exit`, `uuid`, `externalip`, etc.
   */
  def keywordPreview(keyword: String, context: CommandContext): ZIO[Env, CommandError, List[PreviewResult[R]]] =
    IO.fail(NotApplicable)

  /**
   * This event is fired whenever the input text changes (debounced) and the input text starts with the full command name
   * plus any additional text after that (stored in the `rest` field). Override this when you're writing commands that
   * require a prefix and take in 1 long argument after it (spaces aren't parsed as separated arguments in this case).
   */
  def prefixPreview(
    prefix: String,
    rest: String,
    context: CommandContext
  ): ZIO[Env, CommandError, List[PreviewResult[R]]] =
    IO.fail(NotApplicable)

  /**
   * This event is fired whenever the input text changes (debounced) and the input text starts with the full command name
   * plus any additional text after that (arguments are fully parsed passed in as a List). Override this when you're
   * writing commands that behave like fully featured command-line interfaces where you can specify arguments and options.
   */
  def argsPreview(args: List[String], context: CommandContext): ZIO[Env, CommandError, List[PreviewResult[R]]] =
    IO.fail(NotApplicable)

  object Preview {
    def apply[A >: R](a: A): PreviewResult[A] =
      new PreviewResult(Command.this, a, UIO.unit, 1.0, () => DefaultView(title, a.toString).render)

    // TODO: Can this be simplified?
    def unit[A >: R](implicit ev: Command[A] =:= Command[Unit]): PreviewResult[Unit] =
      new PreviewResult(ev(Command.this), (), UIO.unit, 1.0, () => DefaultView(title, "").render)
  }

}

object Command {
  def search(
    commands: Vector[Command[Any]],
    aliases: Map[String, List[String]],
    input: String,
    terminal: CCTerminal
  ): URIO[Env, SearchResults[Any]] = {
    val (commandPart, rest) = input.split("[ ]+", 2) match {
      case Array(prefix, rest) => (prefix, s" $rest")
      case Array(prefix)       => (prefix, "")
    }

    // The user's input + all the matching aliases which have been resolved (expanded) to its full text value
    val aliasedInputs = input :: aliases.getOrElse(commandPart, List.empty).map(_ + rest)

    val context = CommandContext(Language.detect(input), terminal, 1.0)

    def zipWithScore(commands: Vector[Command[Any]], aliasedInput: String): Vector[(Command[Any], Double)] =
      commands.flatMap { c =>
        c.commandNames.map { n =>
          val matchScore = if (n.startsWith(commandPart)) {
            1.0 - (n.length - commandPart.length) / n.length.toDouble
          } else if (n.contains(commandPart)) {
            (1.0 - (n.length - commandPart.length) / n.length.toDouble) * 0.5
          } else {
            0
          }

          val matchScore2 = if (n.startsWith(aliasedInput)) {
            1.0 - (n.length - aliasedInput.length) / n.length.toDouble
          } else if (n.contains(aliasedInput)) {
            (1.0 - (n.length - aliasedInput.length) / n.length.toDouble) * 0.5
          } else {
            0
          }

          (c, matchScore max matchScore2)
        }.maxByOption(_._2).toVector
      }

    for {
      // Aliases don't apply to auto commands
      inputPreviewResults <- ZIO
                              .foreachParN(8) {
                                commands.map(_.inputPreview(input, context))
                              }(_.option) // TODO: Use `.either` here and log errors instead of ignoring them
                              .map(_.flatten.flatten)
      keywordPreviewResults <- ZIO
                                .foreachParN(8) {
                                  aliasedInputs.flatMap { aliasedInput =>
                                    zipWithScore(commands, aliasedInput).collect {
                                      case (c, score) if score > 0 =>
                                        c.keywordPreview(aliasedInput, context.matchScore(score))
                                    }
                                  }
                                }(_.option)
                                .map(_.flatten.flatten)
      prefixPreviewResults <- ZIO
                               .foreachParN(8) {
                                 aliasedInputs.flatMap { aliasedInput =>
                                   aliasedInput.split("[ ]+", 2) match {
                                     case Array(prefix, rest) =>
                                       commands
                                         .filter(_.commandNames.contains(prefix))
                                         .map(_.prefixPreview(prefix, rest, context))
                                     case _ => Vector.empty
                                   }
                                 }
                               }(_.option)
                               .map(_.flatten.flatten)
      argsPreviewResults <- ZIO
                             .foreachParN(8) {
                               aliasedInputs.flatMap { aliasedInput =>
                                 Command.tokenizeArgs(aliasedInput) match {
                                   case command :: args =>
                                     commands
                                       .filter(_.commandNames.exists(_ equalsIgnoreCase command))
                                       .map(_.argsPreview(args, context))
                                   case _ => Vector.empty
                                 }
                               }
                             }(_.option)
                             .map(_.flatten.flatten)
      results = (inputPreviewResults.toVector ++ keywordPreviewResults ++ prefixPreviewResults ++ argsPreviewResults)
        .sortBy(_.score)(Ordering.Double.TotalOrdering.reverse)
        .distinctBy(_.source)
    } yield SearchResults(input, results)
  }

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
      } else if (c == '"') {
        if (quote) {
          tokens += input.substring(start, i)
          quote = false
          start = -1
        } else {
          start = i + 1
          quote = true
        }
      } else if (start < 0 && c != ' ') {
        start = i
      }

      i += 1
    }

    if (start != -1) {
      tokens += input.substring(start)
    }

    tokens.toList
  }

  def parse(config: Config): Either[Throwable, Command[_]] = {
    import io.circe.config.syntax._

    for {
      typeName <- Try(config.getString("type")).toEither
      command <- CommandType.withNameOption(typeName).getOrElse(CommandType.External(typeName)) match {
                  case CommandType.DecodeBase64Command       => config.as[DecodeBase64Command]
                  case CommandType.DecodeUrlCommand          => config.as[DecodeUrlCommand]
                  case CommandType.EncodeBase64Command       => config.as[EncodeBase64Command]
                  case CommandType.EncodeUrlCommand          => config.as[EncodeUrlCommand]
                  case CommandType.EpochMillisCommand        => config.as[EpochMillisCommand]
                  case CommandType.EpochUnixCommand          => config.as[EpochUnixCommand]
                  case CommandType.ExitCommand               => config.as[ExitCommand]
                  case CommandType.ExternalIPCommand         => config.as[ExternalIPCommand]
                  case CommandType.FileNavigationCommand     => config.as[FileNavigationCommand]
                  case CommandType.FindFileCommand           => config.as[FindFileCommand]
                  case CommandType.FindInFileCommand         => config.as[FindInFileCommand]
                  case CommandType.HashCommand               => config.as[HashCommand]
                  case CommandType.ITunesCommand             => config.as[ITunesCommand]
                  case CommandType.LocalIPCommand            => config.as[LocalIPCommand]
                  case CommandType.LockCommand               => config.as[LockCommand]
                  case CommandType.LoremIpsumCommand         => config.as[LoremIpsumCommand]
                  case CommandType.OpenBrowserCommand        => config.as[OpenBrowserCommand]
                  case CommandType.OpacityCommand            => config.as[OpacityCommand]
                  case CommandType.RadixCommand              => config.as[RadixCommand]
                  case CommandType.ResizeCommand             => config.as[ResizeCommand]
                  case CommandType.ReloadCommand             => config.as[ReloadCommand]
                  case CommandType.SearchUrlCommand          => config.as[SearchUrlCommand]
                  case CommandType.TemperatureCommand        => config.as[TemperatureCommand]
                  case CommandType.TerminalCommand           => config.as[TerminalCommand]
                  case CommandType.TimerCommand              => config.as[TimerCommand]
                  case CommandType.ToggleDesktopIconsCommand => config.as[ToggleDesktopIconsCommand]
                  case CommandType.ToggleHiddenFilesCommand  => config.as[ToggleHiddenFilesCommand]
                  case CommandType.UUIDCommand               => config.as[UUIDCommand]
                  case CommandType.WorldTimesCommand         => config.as[WorldTimesCommand]

                  case CommandType.External(typeName) =>
                    Left(
                      circe.DecodingFailure(
                        s"Failed to load command '$typeName' Loading external plugins not support in this mode.",
                        List.empty
                      )
                    )
                }
    } yield command
  }

}

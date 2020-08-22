package commandcenter.command

import java.util.Locale

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.util.OS
import commandcenter.view.syntax._
import commandcenter.view.{ DefaultView, ViewInstances }
import zio._

trait Command[+R] extends ViewInstances {
  val commandType: CommandType

  def commandNames: List[String]
  def title: String
  def locales: Set[Locale] = Set.empty
  def supportedOS: Set[OS] = Set.empty

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[R]]]

  object Preview {
    def apply[A >: R](a: A): PreviewResult[A] =
      new PreviewResult(Command.this, a, UIO.unit, 1.0, () => DefaultView(title, a.toString).render)

    // TODO: Can this be simplified?
    def unit[A >: R](implicit ev: Command[A] =:= Command[Unit]): PreviewResult[Unit] =
      new PreviewResult(ev(Command.this), (), UIO.unit, 1.0, () => DefaultView(title, "").render)
  }

}

object Command {
  def search[A](
    commands: Vector[Command[A]],
    aliases: Map[String, List[String]],
    input: String,
    context: CommandContext
  ): URIO[Env, SearchResults[A]] = {
    val (commandPart, rest) = input.split("[ ]+", 2) match {
      case Array(prefix, rest) => (prefix, s" $rest")
      case Array(prefix)       => (prefix, "")
    }

    // The user's input + all the matching aliases which have been resolved (expanded) to its full text value
    val aliasedInputs = input :: aliases.getOrElse(commandPart, List.empty).map(_ + rest)

    ZIO
      .foreachParN(8) {
        commands.map(command =>
          command
            .preview(SearchInput(input, aliasedInputs, command.commandNames, context))
            .either
            .absorb
            .mapError(t =>
              CommandError.UnexpectedException(t): CommandError
            ) // Defects in a single command are isolated and don't fail the entire search
            .absolve
        )
      }(_.option) // TODO: Use `.either` here and log errors instead of ignoring them
      .map { r =>
        val results = r.flatten.flatten.sortBy(_.score)(Ordering.Double.TotalOrdering.reverse)
        SearchResults(input, results)
      }
  }

  def parse(config: Config): ZManaged[Env, Throwable, Command[_]] =
    for {
      typeName <- Task(config.getString("type")).toManaged_
      command  <- CommandType.withNameOption(typeName).getOrElse(CommandType.External(typeName)) match {
                    case CommandType.CalculatorCommand         => CalculatorCommand.make(config)
                    case CommandType.DecodeBase64Command       => DecodeBase64Command.make(config)
                    case CommandType.DecodeUrlCommand          => DecodeUrlCommand.make(config)
                    case CommandType.EncodeBase64Command       => EncodeBase64Command.make(config)
                    case CommandType.EncodeUrlCommand          => EncodeUrlCommand.make(config)
                    case CommandType.EpochMillisCommand        => EpochMillisCommand.make(config)
                    case CommandType.EpochUnixCommand          => EpochUnixCommand.make(config)
                    case CommandType.ExitCommand               => ExitCommand.make(config)
                    case CommandType.ExternalIPCommand         => ExternalIPCommand.make(config)
                    case CommandType.FileNavigationCommand     => FileNavigationCommand.make(config)
                    case CommandType.FindFileCommand           => FindFileCommand.make(config)
                    case CommandType.FindInFileCommand         => FindInFileCommand.make(config)
                    case CommandType.HashCommand               => HashCommand.make(config)
                    case CommandType.ITunesCommand             => ITunesCommand.make(config)
                    case CommandType.LocalIPCommand            => LocalIPCommand.make(config)
                    case CommandType.LockCommand               => LockCommand.make(config)
                    case CommandType.LoremIpsumCommand         => LoremIpsumCommand.make(config)
                    case CommandType.OpacityCommand            => OpacityCommand.make(config)
                    case CommandType.OpenBrowserCommand        => OpenBrowserCommand.make(config)
                    case CommandType.ProcessIdCommand          => ProcessIdCommand.make(config)
                    case CommandType.RadixCommand              => RadixCommand.make(config)
                    case CommandType.ReloadCommand             => ReloadCommand.make(config)
                    case CommandType.ResizeCommand             => ResizeCommand.make(config)
                    case CommandType.SearchMavenCommand        => SearchMavenCommand.make(config)
                    case CommandType.SearchUrlCommand          => SearchUrlCommand.make(config)
                    case CommandType.SnippetsCommand           => SnippetsCommand.make(config)
                    case CommandType.SuspendProcessCommand     => SuspendProcessCommand.make(config)
                    case CommandType.TemperatureCommand        => TemperatureCommand.make(config)
                    case CommandType.TerminalCommand           => TerminalCommand.make(config)
                    case CommandType.TimerCommand              => TimerCommand.make(config)
                    case CommandType.ToggleDesktopIconsCommand => ToggleDesktopIconsCommand.make(config)
                    case CommandType.ToggleHiddenFilesCommand  => ToggleHiddenFilesCommand.make(config)
                    case CommandType.UUIDCommand               => UUIDCommand.make(config)
                    case CommandType.WorldTimesCommand         => WorldTimesCommand.make(config)

                    case CommandType.External(typeName) =>
                      ZManaged.fail(
                        new UnsupportedOperationException(
                          s"Failed to load command '$typeName' Loading external plugins not support in this mode."
                        )
                      )
                  }
    } yield command

}

package commandcenter.command

import com.monovore.decline.Help
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.event.KeyboardShortcut
import commandcenter.util.{ JavaVM, OS }
import commandcenter.view.syntax._
import commandcenter.view.{ DefaultView, ViewInstances }
import zio._
import zio.logging._
import zio.stream.ZStream

import java.util.Locale

trait Command[+R] extends ViewInstances {
  val commandType: CommandType

  def commandNames: List[String]
  def title: String
  def locales: Set[Locale]             = Set.empty
  def supportedOS: Set[OS]             = Set.empty
  def shortcuts: Set[KeyboardShortcut] = Set.empty

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[R]]

  object Preview {
    def apply[A >: R](a: A): PreviewResult[A] =
      new PreviewResult(Command.this, a, UIO.unit, 1.0, () => DefaultView(title, a.toString).render)

    def unit[A >: R](implicit ev: Command[A] =:= Command[Unit]): PreviewResult[Unit] =
      new PreviewResult(ev(Command.this), (), UIO.unit, 1.0, () => DefaultView(title, "").render)

    def help[A >: R](help: Help)(implicit ev: Command[A] =:= Command[Unit]): PreviewResult[Unit] =
      new PreviewResult(
        ev(Command.this),
        (),
        UIO.unit,
        1.0,
        () => DefaultView(title, HelpMessage.formatted(help)).render
      )
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

    (for {
      chunks <- ZStream
                  .fromIterable(commands)
                  .mapMPar(8) { command =>
                    command
                      .preview(SearchInput(input, aliasedInputs, command.commandNames, context))
                      .flatMap {
                        case PreviewResults.Single(r)               => UIO(Chunk.single(r))
                        case PreviewResults.Multiple(rs)            => UIO(rs)
                        case PreviewResults.Paginated(rs, pageSize) =>
                          // TODO: Add "More..." PreviewResult
                          rs.take(pageSize).runCollect
                      }
                      .catchAllDefect(t => ZIO.fail(CommandError.UnexpectedException(t)))
                      .either

                  }
                  .runCollect
    } yield chunks.flatMap {
      case Left(e)  => Chunk.single(Left(e))
      case Right(r) => r.map(Right(_))
    }.partitionMap(identity) match {
      case (errors, successes) =>
        val results = successes.sortBy(_.score)(Ordering.Double.TotalOrdering.reverse)

        SearchResults(input, results, errors)
    }).tap { r =>
      ZIO.foreach_(r.errors) {
        case CommandError.UnexpectedException(t) =>
          log.throwable(s"Command encountered an unexpected exception with input: $input", t)

        case _ => ZIO.unit
      }
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
                    case CommandType.HoogleCommand             => HoogleCommand.make(config)
                    case CommandType.ITunesCommand             => ITunesCommand.make(config)
                    case CommandType.LocalIPCommand            => LocalIPCommand.make(config)
                    case CommandType.LockCommand               => LockCommand.make(config)
                    case CommandType.LoremIpsumCommand         => LoremIpsumCommand.make(config)
                    case CommandType.OpacityCommand            => OpacityCommand.make(config)
                    case CommandType.OpenBrowserCommand        => OpenBrowserCommand.make(config)
                    case CommandType.ProcessIdCommand          => ProcessIdCommand.make(config)
                    case CommandType.RadixCommand              => RadixCommand.make(config)
                    case CommandType.RebootCommand             => RebootCommand.make(config)
                    case CommandType.ReloadCommand             => ReloadCommand.make(config)
                    case CommandType.ResizeCommand             => ResizeCommand.make(config)
                    case CommandType.SearchCratesCommand       => SearchCratesCommand.make(config)
                    case CommandType.SearchMavenCommand        => SearchMavenCommand.make(config)
                    case CommandType.SearchUrlCommand          => SearchUrlCommand.make(config)
                    case CommandType.SnippetsCommand           => SnippetsCommand.make(config)
                    case CommandType.StocksCommand             => StocksCommand.make(config)
                    case CommandType.SuspendProcessCommand     => SuspendProcessCommand.make(config)
                    case CommandType.SwitchWindowCommand       => SwitchWindowCommand.make(config)
                    case CommandType.SystemCommand             => SystemCommand.make(config)
                    case CommandType.TemperatureCommand        => TemperatureCommand.make(config)
                    case CommandType.TerminalCommand           => TerminalCommand.make(config)
                    case CommandType.TimerCommand              => TimerCommand.make(config)
                    case CommandType.ToggleDesktopIconsCommand => ToggleDesktopIconsCommand.make(config)
                    case CommandType.ToggleHiddenFilesCommand  => ToggleHiddenFilesCommand.make(config)
                    case CommandType.UUIDCommand               => UUIDCommand.make(config)
                    case CommandType.WorldTimesCommand         => WorldTimesCommand.make(config)

                    case CommandType.External(typeName) if JavaVM.isSubstrateVM =>
                      ZManaged.fail(CommandPluginError.PluginsNotSupported(typeName))

                    case CommandType.External(typeName) =>
                      CommandPlugin.loadDynamically(config, typeName)
                  }
    } yield command

}

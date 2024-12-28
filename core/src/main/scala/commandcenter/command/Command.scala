package commandcenter.command

import com.monovore.decline.Help
import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.event.KeyboardShortcut
import commandcenter.util.JavaVM
import commandcenter.util.OS
import commandcenter.view.Rendered
import commandcenter.view.Renderer
import fansi.Color
import zio.*
import zio.stream.ZSink
import zio.stream.ZStream

import java.util.Locale

trait Command[+A] {
  val commandType: CommandType

  def commandNames: List[String]
  def title: String
  def locales: Set[Locale] = Set.empty
  def supportedOS: Set[OS] = Set.empty
  def shortcuts: Set[KeyboardShortcut] = Set.empty

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[A]]

  object Preview {

    def apply[A1 >: A](a: A1): PreviewResult[A1] =
      PreviewResult.Some(
        Command.this,
        a,
        ZIO.unit,
        RunOption.Hide,
        MoreResults.Exhausted,
        1.0,
        () => Renderer.renderDefault(title, a.toString)
      )

    def unit[A1 >: A](implicit ev: Command[A1] =:= Command[Unit]): PreviewResult[Unit] =
      PreviewResult.Some(
        ev(Command.this),
        (),
        ZIO.unit,
        RunOption.Hide,
        MoreResults.Exhausted,
        1.0,
        () => Renderer.renderDefault(title, "")
      )

    def help[A1 >: A](help: Help)(implicit ev: Command[A1] =:= Command[Unit]): PreviewResult[Unit] =
      PreviewResult.Some(
        ev(Command.this),
        (),
        ZIO.unit,
        RunOption.Hide,
        MoreResults.Exhausted,
        1.0,
        () => Renderer.renderDefault(title, HelpMessage.formatted(help))
      )
  }

}

object Command {

  private val previewParallelism: Int = 8

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
      _ <- ZIO.logTrace(s"Searching on input `$input` for ${commands.length} commands")
      chunks <- if (input.isEmpty)
                  ZIO.succeed(Chunk.empty)
                else
                  ZStream
                    .fromIterable(commands)
                    .mapZIOParUnordered(previewParallelism) { command =>
                      command
                        .preview(SearchInput(input, aliasedInputs, command.commandNames, context))
                        .flatMap {
                          case PreviewResults.Single(r)    => ZIO.succeed(Chunk.single(r))
                          case PreviewResults.Multiple(rs) => ZIO.succeed(rs)
                          case p @ PreviewResults.Paginated(
                                rs,
                                initialPageSize,
                                _,
                                totalRemaining
                              ) =>
                            for {
                              (results, restStream) <- Scope.global.use {
                                                         rs.peel(ZSink.take[PreviewResult[A]](initialPageSize))
                                                       }
                            } yield results match {
                              case beforeLast :+ last if results.length >= initialPageSize =>
                                if (beforeLast.isEmpty)
                                  beforeLast :+ last
                                    .runOption(RunOption.RemainOpen)
                                    .moreResults(
                                      MoreResults.Remaining(
                                        p.copy(
                                          results = restStream,
                                          totalRemaining = totalRemaining.map(_ - initialPageSize)
                                        )
                                      )
                                    )
                                else
                                  beforeLast :+ last :+
                                    PreviewResult
                                      .nothing(Rendered.Ansi(Color.Yellow(p.moreMessage)))
                                      .runOption(RunOption.RemainOpen)
                                      .score(last.score)
                                      .moreResults(
                                        MoreResults.Remaining(
                                          p.copy(
                                            results = restStream,
                                            totalRemaining = totalRemaining.map(_ - initialPageSize)
                                          )
                                        )
                                      )

                              case chunk => chunk
                            }
                        }
                        .catchAllDefect(t => ZIO.fail(CommandError.UnexpectedError(t, command)))
                        .either
//                        .timed
//                        .flatMap { case (timeTaken, r) =>
//                          ZIO.logTrace(s"Preview `${command.title}` took ${timeTaken.render}").as(r)
//                        }
                    }
                    .runCollect
                    .timed
                    .flatMap { case (timeTaken, r) =>
                      ZIO.logTrace(s"All previews took ${timeTaken.render} (parallelism=$previewParallelism)").as(r)
                    }
    } yield chunks.flatMap {
      case Left(e)  => Chunk.single(Left(e))
      case Right(r) => r.map(Right(_))
    }.partitionMap(identity) match {
      case (errors, successes) =>
        val errorsWithMessages = errors.collect { case e: CommandError.ShowMessage =>
          e.previewResult
        }

        val results = (successes ++ errorsWithMessages).sortBy(_.score)(Ordering.Double.TotalOrdering.reverse)

        SearchResults(input, results, errors)
    }).tap { r =>
      ZIO.foreachDiscard(r.errors) {
        case CommandError.UnexpectedError(t, source) =>
          ZIO.logWarningCause(
            s"Command `${source.commandType}` encountered an unexpected exception with input: $input",
            Cause.die(t)
          )

        case _ => ZIO.unit
      }
    }

  }

  def parse(config: Config): ZIO[Scope & Env, CommandPluginError, Command[?]] =
    for {
      typeName <- ZIO.attempt(config.getString("type")).mapError(CommandPluginError.UnexpectedException.apply)
      command <- CommandType.withNameOption(typeName).getOrElse(CommandType.External(typeName)) match {
                   case CommandType.CalculatorCommand         => CalculatorCommand.make(config)
                   case CommandType.ConfigCommand             => ConfigCommand.make(config)
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
                   case CommandType.Foobar2000Command         => Foobar2000Command.make(config)
                   case CommandType.HashCommand               => HashCommand.make(config)
                   case CommandType.HoogleCommand             => HoogleCommand.make(config)
                   case CommandType.ITunesCommand             => ITunesCommand.make(config)
                   case CommandType.LocalIPCommand            => LocalIPCommand.make(config)
                   case CommandType.LockCommand               => LockCommand.make(config)
                   case CommandType.LoremIpsumCommand         => LoremIpsumCommand.make(config)
                   case CommandType.MuteCommand               => MuteCommand.make(config)
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
                   case CommandType.SpeakCommand              => SpeakCommand.make(config)
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
                     ZIO.fail(CommandPluginError.PluginsNotSupported(typeName))

                   case CommandType.External(typeName) =>
                     CommandPlugin.loadDynamically(config, typeName)
                 }
    } yield command

}

package commandcenter.command

import com.monovore.decline.Help
import com.typesafe.config.Config
import commandcenter.event.KeyboardShortcut
import commandcenter.util.{JavaVM, OS}
import commandcenter.view.{Rendered, Renderer}
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import fansi.{Color, Str}
import zio.*
import zio.stream.{ZSink, ZStream}

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

  /** Searches for commands matching the given input.
    *
    * The user's raw input, plus every alias are each expanded and searched
    * independently, then merged. A single alias name can map to multiple
    * targets (e.g. `"hex" = ["radix --to 16", "radix --from 16"]`), and each
    * target is searched independently so that all of them can surface their own
    * result, instead of only the first match winning.
    */
  def search[A](
      commands: Vector[Command[A]],
      aliases: Map[String, List[String]],
      input: String,
      context: CommandContext
  ): URIO[Env, SearchResults[A]] =
    if (input.isEmpty)
      ZIO.succeed(SearchResults(input, Chunk.empty))
    else {
      val (commandPart, rest) = input.split("[ ]+", 2) match {
        case Array(prefix, rest) => (prefix, s" $rest")
        case Array(prefix)       => (prefix, "")
      }

      // The user's input, plus every matching alias resolved (expanded) to its full text value.
      val candidates = (input :: aliases.getOrElse(commandPart, List.empty).map(_ + rest)).distinct

      for {
        candidateResults <- ZIO.foreach(Chunk.fromIterable(candidates))(searchExpandedInput(commands, _, context))
      } yield SearchResults(
        input,
        candidateResults.flatMap(_.previews).sortBy(_.score)(Ordering.Double.TotalOrdering.reverse),
        candidateResults.flatMap(_.errors)
      )
    }

  /** Searches for commands matching a single already alias-expanded input
    * string, splitting it into semicolon-separated statements first if it
    * contains any.
    */
  private def searchExpandedInput[A](
      commands: Vector[Command[A]],
      input: String,
      context: CommandContext
  ): URIO[Env, SearchResults[A]] =
    if (!input.contains(';'))
      searchStatement(commands, input, context)
    else
      SearchInput.splitStatements(input) match {
        case Nil              => ZIO.succeed(SearchResults(input, Chunk.empty))
        case statement :: Nil => searchStatement(commands, statement, context).map(_.copy(searchTerm = input))
        case statements       =>
          for {
            statementResults <- ZIO.foreach(Chunk.fromIterable(statements))(searchStatement(commands, _, context))
          } yield SearchResults(
            input,
            combinedRunAllPreview(statementResults).fold(Chunk.empty[PreviewResult[A]])(Chunk.single) ++
              statementResults.flatMap(_.previews),
            statementResults.flatMap(_.errors)
          )
      }

  /** Synthesizes a single combined entry that, when run, sequentially runs the
    * top (highest-scored) match of every statement, in the order they were
    * typed.
    *
    * Returns None if any statement failed to match anything.
    */
  private def combinedRunAllPreview[A](statementResults: Chunk[SearchResults[A]]): Option[PreviewResult[A]] = {
    // Each statement's own previews aren't sorted (only the fully merged list is, once, in `search`), so the
    // top match has to be found with `maxByOption` here rather than assumed to be the head.
    val topPerStatement = statementResults.map(_.previews.maxByOption(_.score))

    Option.when(topPerStatement.nonEmpty && topPerStatement.forall(_.isDefined)) {
      val topResults = topPerStatement.flatten

      val runAll = ZIO.foreachDiscard(topResults)(_.onRunSandboxedLogged)

      val runOption =
        if (topResults.exists(_.runOption == RunOption.Exit)) RunOption.Exit
        else topResults.last.runOption

      val combinedScore = topResults.map(_.score).max + 1
      val description = statementResults.map(_.searchTerm.trim).mkString("; ")

      PreviewResult
        .nothing(Renderer.renderDefault(s"Run ${topResults.length} commands", Str(description)))
        .onRun(runAll)
        .runOption(runOption)
        .score(combinedScore)
    }
  }

  /** Runs a search for a single command statement (i.e. with no
    * semicolon-separated statements, and no further alias expansion - both of
    * those are already resolved by the time this is called from [[search]] /
    * [[searchExpandedInput]]).
    */
  private def searchStatement[A](
      commands: Vector[Command[A]],
      input: String,
      context: CommandContext
  ): URIO[Env, SearchResults[A]] =
    (for {
      _            <- ZIO.logTrace(s"Searching on input `$input` for ${commands.length} commands")
      resultChunks <- if (input.isEmpty)
                        ZIO.succeed(Chunk.empty)
                      else
                        ZStream
                          .fromIterable(commands)
                          .mapZIOPar(previewParallelism) { command =>
                            command
                              .preview(SearchInput(input, List(input), command.commandNames, context))
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
                          }
                          .runCollect
                          .timed
                          .flatMap { case (timeTaken, r) =>
                            ZIO
                              .logTrace(s"All previews took ${timeTaken.render} (parallelism=$previewParallelism)")
                              .as(r)
                          }
    } yield resultChunks.flatMap {
      case Left(e)  => Chunk.single(Left(e))
      case Right(r) => r.map(Right(_))
    }.partitionMap(identity) match {
      case (errors, successes) =>
        val errorsWithMessages = errors.collect { case e: CommandError.ShowMessage =>
          e.previewResult
        }

        SearchResults(input, successes ++ errorsWithMessages, errors)
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

  def parse(config: Config): ZIO[Scope & Env, CommandPluginError, Command[?]] =
    for {
      typeName <- ZIO.attempt(config.getString("type")).mapError(CommandPluginError.UnexpectedException.apply)
      command  <- CommandType.withNameOption(typeName).getOrElse(CommandType.External(typeName)) match {
                   case CommandType.CalculatorCommand         => CalculatorCommand.make(config)
                   case CommandType.ChessCommand              => ChessCommand.make(config)
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
                   case CommandType.HttpCommand               => HttpCommand.make(config)
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
                   case CommandType.ShellCommand              => ShellCommand.make(config)
                   case CommandType.SnippetsCommand           => SnippetsCommand.make(config)
                   case CommandType.SpeakCommand              => SpeakCommand.make(config)
                   case CommandType.SuspendProcessCommand     => SuspendProcessCommand.make(config)
                   case CommandType.SwitchWindowCommand       => SwitchWindowCommand.make(config)
                   case CommandType.SystemCommand             => SystemCommand.make(config)
                   case CommandType.TerminalCommand           => TerminalCommand.make(config)
                   case CommandType.TimerCommand              => TimerCommand.make(config)
                   case CommandType.ToggleDesktopIconsCommand => ToggleDesktopIconsCommand.make(config)
                   case CommandType.ToggleHiddenFilesCommand  => ToggleHiddenFilesCommand.make(config)
                   case CommandType.UnitConversionCommand     => UnitConversionCommand.make(config)
                   case CommandType.UUIDCommand               => UUIDCommand.make(config)
                   case CommandType.WorldTimesCommand         => WorldTimesCommand.make(config)

                   case CommandType.External(typeName) if JavaVM.isSubstrateVM =>
                     ZIO.fail(CommandPluginError.PluginsNotSupported(typeName))

                   case CommandType.External(typeName) =>
                     CommandPlugin.loadDynamically(config, typeName)
                 }
    } yield command

}

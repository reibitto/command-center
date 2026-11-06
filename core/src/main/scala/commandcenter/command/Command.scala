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

import java.util.Locale

trait Command[+A] {
  val commandType: CommandType

  def commandNames: List[String]
  def title: String
  def locales: Set[Locale] = Set.empty
  def supportedOS: Set[OS] = Set.empty
  def shortcuts: Set[KeyboardShortcut] = Set.empty

  // If true, input with `;` statements is also passed to this command unsplit (e.g. `gcd 20; 16`)
  def allowsSemicolons: Boolean = false

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[A]]

  object Preview {

    def apply[A1 >: A](a: A1): PreviewResult[A1] = some(Command.this, a, a.toString)

    def unit[A1 >: A](implicit ev: Command[A1] =:= Command[Unit]): PreviewResult[Unit] =
      some(ev(Command.this), (), "")

    def help[A1 >: A](help: Help)(implicit ev: Command[A1] =:= Command[Unit]): PreviewResult[Unit] =
      some(ev(Command.this), (), HelpMessage.formatted(help))

    private def some[B](command: Command[B], b: B, content: => Str): PreviewResult[B] =
      PreviewResult.Some(
        command,
        b,
        ZIO.unit,
        RunOption.Hide,
        MoreResults.Exhausted,
        1.0,
        () => Renderer.renderDefault(title, content)
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
    else
      // Paginated streams stay open in this scope until the results are released (closed right away on failure)
      ZIO.acquireReleaseExitWith(Scope.make)((scope: Scope.Closeable, exit: Exit[Any, Any]) =>
        scope.close(exit).unless(exit.isSuccess)
      ) { scope =>
        scope
          .extend[Env](
            ZIO.foreach(Chunk.fromIterable(expandAliases(aliases, input)))(
              searchCandidate(commands, aliases, _, context)
            )
          )
          .map { candidateResults =>
            SearchResults(
              input,
              candidateResults.flatMap(_.previews).sortBy(_.score)(Ordering.Double.TotalOrdering.reverse),
              candidateResults.flatMap(_.errors),
              // Forked, since closing waits for a page that's still loading (e.g. an HTTP request)
              release = scope.close(Exit.unit).forkDaemon.unit
            )
          }
      }

  // The input plus every alias target its first word maps to.
  private def expandAliases(aliases: Map[String, List[String]], input: String): List[String] = {
    val (commandPart, rest) = SearchInput.splitCommandAndRest(input)

    (input :: aliases.getOrElse(commandPart, List.empty).map(_ + rest)).distinct
  }

  /** Searches an alias-expanded line. A line with multiple `;` statements is
    * searched per statement (with each statement's own aliases expanded), plus
    * a combined "run all" entry. Commands with `allowsSemicolons` also get the
    * unsplit line.
    */
  private def searchCandidate[A](
      commands: Vector[Command[A]],
      aliases: Map[String, List[String]],
      input: String,
      context: CommandContext
  ): URIO[Env & Scope, SearchResults[A]] =
    SearchInput.splitStatements(input) match {
      case statements @ (_ :: _ :: _) =>
        for {
          whole           <- searchStatement(commands.filter(_.allowsSemicolons), input, context)
          statementResult <- ZIO.foreach(Chunk.fromIterable(statements)) { statement =>
                               ZIO
                                 .foreach(Chunk.fromIterable(expandAliases(aliases, statement)))(
                                   searchStatement(commands, _, context)
                                 )
                                 .map(rs => SearchResults(statement, rs.flatMap(_.previews), rs.flatMap(_.errors)))
                             }
        } yield SearchResults(
          input,
          Chunk.fromIterable(combinedRunAllPreview(statementResult)) ++ whole.previews ++
            statementResult.flatMap(_.previews),
          whole.errors ++ statementResult.flatMap(_.errors)
        )

      case statement :: Nil => searchStatement(commands, statement, context).map(_.copy(searchTerm = input))
      case Nil              => ZIO.succeed(SearchResults(input, Chunk.empty))
    }

  /** Runs a single command directly by its own canonical name, bypassing
    * whatever is actually typed in the input box. Used so a command's
    * configured `shortcuts` can still trigger it even if not in the preview
    * results.
    */
  def searchByCommandName[A](command: Command[A], context: CommandContext): URIO[Env, Option[PreviewResult[A]]] =
    command.commandNames.headOption match {
      // Released right away since the preview is only run, never paged
      case Some(name) =>
        search(Vector(command), Map.empty, name, context).flatMap(r => r.release.as(r.previews.maxByOption(_.score)))
      case None => ZIO.none
    }

  /** Synthesizes a single combined entry that, when run, sequentially runs the
    * top (highest-scored) match of every statement, in the order they were
    * typed.
    *
    * Returns None if any statement has no real match. Error messages and
    * fallbacks (e.g. searching the web for the raw input) don't count.
    */
  private def combinedRunAllPreview[A](statementResults: Chunk[SearchResults[A]]): Option[PreviewResult[A]] = {
    // Error messages are `PreviewResult.None` rows, so only real command results (`Some`) count
    val topPerStatement = statementResults.map(
      _.previews.collect { case p: PreviewResult.Some[A] if !p.isFallback => p }.maxByOption(_.score)
    )

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

  /** Runs a search for a single, already alias-expanded command statement. */
  private def searchStatement[A](
      commands: Vector[Command[A]],
      input: String,
      context: CommandContext
  ): URIO[Env & Scope, SearchResults[A]] =
    (for {
      _            <- ZIO.logTrace(s"Searching on input `$input` for ${commands.length} commands")
      resultChunks <-
        if (input.isEmpty)
          ZIO.succeed(Chunk.empty)
        else
          ZIO
            .foreachPar(Chunk.fromIterable(commands)) { command =>
              // `suspendSucceed` so that exceptions thrown while constructing the effect become defects
              ZIO
                .suspendSucceed(command.preview(SearchInput(input, List(input), command.commandNames, context)))
                .flatMap {
                  case PreviewResults.Single(r)       => ZIO.succeed(Chunk.single(r))
                  case PreviewResults.Multiple(rs)    => ZIO.succeed(rs)
                  case p: PreviewResults.Paginated[A] =>
                    for {
                      reader          <- PageReader.make(p.results)
                      (page, hasMore) <- reader.nextPage(p.initialPageSize)
                    } yield page match {
                      case beforeLast :+ last if hasMore =>
                        val more = MoreResults.Remaining(
                          reader,
                          p.initialPageSize,
                          p.morePageSize,
                          p.totalRemaining.map(_ - page.length)
                        )

                        if (beforeLast.isEmpty)
                          beforeLast :+ last.runOption(RunOption.RemainOpen).moreResults(more)
                        else
                          beforeLast :+ last :+
                            PreviewResult
                              .nothing(Rendered.Ansi(Color.Yellow(more.moreMessage)))
                              .runOption(RunOption.RemainOpen)
                              .score(last.score)
                              .moreResults(more)

                      case _ => page
                    }
                }
                .catchAllDefect(t => ZIO.fail(CommandError.UnexpectedError(t, command)))
                .either
            }
            .withParallelism(previewParallelism)
            .timed
            .flatMap { case (timeTaken, r) =>
              ZIO
                .logTrace(s"All previews took ${timeTaken.render} (parallelism=$previewParallelism)")
                .as(r)
            }
    } yield {
      val (errors, successes) = resultChunks.partitionMap(identity)
      val errorsWithMessages = errors.collect { case e: CommandError.ShowMessage => e.previewResult }

      SearchResults(input, successes.flatten ++ errorsWithMessages, errors)
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
      command  <- CommandType.withNameOption(typeName) match {
                   case Some(builtIn: CommandType.BuiltIn) =>
                     builtIn.plugin.make(config).mapError(CommandPluginError.fromThrowable)

                   case _ if JavaVM.isSubstrateVM =>
                     ZIO.fail(CommandPluginError.PluginsNotSupported(typeName))

                   case _ =>
                     CommandPlugin.loadDynamically(config, typeName)
                 }
    } yield command

}

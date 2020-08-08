package commandcenter.command

import java.util.Locale

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.util.OS
import commandcenter.view.syntax._
import commandcenter.view.{ DefaultView, ViewInstances }
import io.circe
import zio._

import scala.util.Try

trait Command[+R] extends ViewInstances {
  val commandType: CommandType

  def commandNames: List[String]

  // TODO: LocalizedString
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

  def parse(config: Config): Either[Throwable, Command[_]] = {
    import io.circe.config.syntax._

    for {
      typeName <- Try(config.getString("type")).toEither
      command  <- CommandType.withNameOption(typeName).getOrElse(CommandType.External(typeName)) match {
                    case CommandType.CalculatorCommand         => config.as[CalculatorCommand]
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
                    case CommandType.OpacityCommand            => config.as[OpacityCommand]
                    case CommandType.OpenBrowserCommand        => config.as[OpenBrowserCommand]
                    case CommandType.RadixCommand              => config.as[RadixCommand]
                    case CommandType.ReloadCommand             => config.as[ReloadCommand]
                    case CommandType.ResizeCommand             => config.as[ResizeCommand]
                    case CommandType.SearchMavenCommand        => config.as[SearchMavenCommand]
                    case CommandType.SearchUrlCommand          => config.as[SearchUrlCommand]
                    case CommandType.SuspendProcessCommand     => config.as[SuspendProcessCommand]
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

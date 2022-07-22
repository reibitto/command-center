package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.Pointer
import com.typesafe.config.Config
import commandcenter.command.Foobar2000Command.Opt
import commandcenter.util.OS
import commandcenter.util.WindowManager.fromCString
import commandcenter.view.{Rendered, Renderer}
import commandcenter.CCRuntime.Env
import zio.{IO, Scope, System, Task, ZIO}
import zio.process.Command as PCommand

import java.io.File
import java.nio.file.Paths

final case class Foobar2000Command(commandNames: List[String], foobarPath: File) extends Command[Unit] {
  val commandType: CommandType = CommandType.Foobar2000Command
  val title: String = "foobar2000"

  override val supportedOS: Set[OS] = Set(OS.Windows)

  val playCommand = decline.Command("play", "Start the current track")(Opts(Opt.Play))
  val pauseCommand = decline.Command("pause", "Pause the current track")(Opts(Opt.Pause))
  val stopCommand = decline.Command("stop", "Stop the current track")(Opts(Opt.Stop))
  val nextTrackCommand = decline.Command("next", "Switch to next track")(Opts(Opt.NextTrack))
  val previousTrackCommand = decline.Command("previous", "Switch to previous track")(Opts(Opt.PreviousTrack))
  val rewindCommand = decline.Command("rewind", "Stop the current track")(Opts(Opt.Rewind))
  val deleteTrackCommand = decline.Command("delete", "Delete the current track")(Opts(Opt.DeleteTrack))
  val helpCommand = decline.Command("help", "Display command usage")(Opts(Opt.Help))

  val opts = Opts.subcommand(playCommand) orElse
    Opts.subcommand(pauseCommand) orElse
    Opts.subcommand(stopCommand) orElse
    Opts.subcommand(nextTrackCommand) orElse
    Opts.subcommand(previousTrackCommand) orElse
    Opts.subcommand(rewindCommand) orElse
    Opts.subcommand(deleteTrackCommand) orElse
    Opts.subcommand(helpCommand) orNone

  val foobarCommand = decline.Command("foobar", "foobar2000 commands")(opts)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      opt <-
        ZIO
          .fromEither(foobarCommand.parse(input.args))
          .mapBoth(
            help => CommandError.ShowMessage(Rendered.Ansi(HelpMessage.formatted(help)), Scores.high(input.context)),
            _.getOrElse(Opt.Show)
          )
      run = opt match {
              case Opt.Play          => PCommand(foobarPath.getCanonicalPath, "/play").successfulExitCode
              case Opt.Pause         => PCommand(foobarPath.getCanonicalPath, "/pause").successfulExitCode
              case Opt.Stop          => PCommand(foobarPath.getCanonicalPath, "/stop").successfulExitCode
              case Opt.NextTrack     => PCommand(foobarPath.getCanonicalPath, "/next").successfulExitCode
              case Opt.PreviousTrack => PCommand(foobarPath.getCanonicalPath, "/prev").successfulExitCode
              case Opt.Rewind =>
                PCommand(foobarPath.getCanonicalPath, "/stop").successfulExitCode *> PCommand(
                  foobarPath.getCanonicalPath,
                  "/play"
                ).successfulExitCode
              case Opt.DeleteTrack =>
                PCommand(
                  foobarPath.getCanonicalPath,
                  "/playing_command:File Operations/Delete file(s)"
                ).successfulExitCode
              case Opt.Show =>
                PCommand(foobarPath.getCanonicalPath, "/show").successfulExitCode
              case Opt.Help => ZIO.unit
            }
      message <- opt match {
                   case Opt.Play          => ZIO.succeed(fansi.Str(playCommand.header))
                   case Opt.Pause         => ZIO.succeed(fansi.Str(pauseCommand.header))
                   case Opt.Stop          => ZIO.succeed(fansi.Str(stopCommand.header))
                   case Opt.NextTrack     => ZIO.succeed(fansi.Str(nextTrackCommand.header))
                   case Opt.PreviousTrack => ZIO.succeed(fansi.Str(previousTrackCommand.header))
                   case Opt.Rewind        => ZIO.succeed(fansi.Str("Rewind current track to the beginning"))
                   case Opt.DeleteTrack   => ZIO.succeed(fansi.Str(deleteTrackCommand.header))
                   case Opt.Help          => ZIO.succeed(fansi.Str(foobarCommand.showHelp))
                   case Opt.Show =>
                     for {
                       trackInfo <- trackInfoFromWindow.catchAll(_ => ZIO.none)
                     } yield trackInfo
                       .map(t => fansi.Color.Magenta(t))
                       .getOrElse(fansi.Str("Show window"))

                 }
    } yield PreviewResults.one(
      Preview.unit
        .onRun(run.unit)
        .score(Scores.high(input.context))
        .rendered(Renderer.renderDefault(title, message))
    )

  private def trackInfoFromWindow: Task[Option[String]] =
    ZIO.async { cb =>
      User32.INSTANCE.EnumWindows(
        (window: HWND, _: Pointer) => {
          val windowTitle = fromCString(256)(a => User32.INSTANCE.GetWindowText(window, a, a.length))

          if (windowTitle.contains("[foobar2000]")) {
            cb(ZIO.some(windowTitle.replace("[foobar2000]", "").trim))
            false
          } else {
            true
          }
        },
        Pointer.NULL
      )

      cb(ZIO.none)

    }
}

object Foobar2000Command extends CommandPlugin[Foobar2000Command] {

  def make(config: Config): IO[CommandPluginError, Foobar2000Command] = {
    import commandcenter.config.Decoders.*

    for {
      commandNames  <- config.getZIO[Option[List[String]]]("commandNames")
      foobarPathOpt <- config.getZIO[Option[File]]("foobarPath")
      foobarPath    <- ZIO.succeed(foobarPathOpt).someOrElseZIO(findFoobarPath)
    } yield Foobar2000Command(commandNames.getOrElse(List("foobar", "fb")), foobarPath)
  }

  private def findFoobarPath: IO[CommandPluginError, File] =
    System
      .env("ProgramFiles(x86)")
      .map(_.map(pf => Paths.get(pf).resolve("foobar2000/foobar2000.exe").toFile).filter(_.exists))
      .some
      .orElse {
        System
          .env("ProgramFiles")
          .map(_.map(pf => Paths.get(pf).resolve("foobar2000/foobar2000.exe").toFile).filter(_.exists))
          .some
      }
      .unsome
      .mapError(CommandPluginError.UnexpectedException)
      .someOrFail(
        CommandPluginError.PluginNotApplicable(CommandType.Foobar2000Command, "Path to foobar2000 executable not found")
      )

  sealed trait Opt

  case object Opt {
    case object Play extends Opt
    case object Pause extends Opt
    case object Stop extends Opt
    case object NextTrack extends Opt
    case object PreviousTrack extends Opt
    case object Rewind extends Opt
    case object DeleteTrack extends Opt
    case object Show extends Opt
    case object Help extends Opt
  }
}

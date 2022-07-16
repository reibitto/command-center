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
import commandcenter.CCRuntime.{Env, PartialEnv}
import zio.{system, Task, UIO, ZIO, ZManaged}
import zio.process.Command as PCommand
import zio.system.System

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
                   case Opt.Play          => UIO(fansi.Str(playCommand.header))
                   case Opt.Pause         => UIO(fansi.Str(pauseCommand.header))
                   case Opt.Stop          => UIO(fansi.Str(stopCommand.header))
                   case Opt.NextTrack     => UIO(fansi.Str(nextTrackCommand.header))
                   case Opt.PreviousTrack => UIO(fansi.Str(previousTrackCommand.header))
                   case Opt.Rewind        => UIO(fansi.Str("Rewind current track to the beginning"))
                   case Opt.DeleteTrack   => UIO(fansi.Str(deleteTrackCommand.header))
                   case Opt.Help          => UIO(fansi.Str(foobarCommand.showHelp))
                   case Opt.Show =>
                     for {
                       trackInfo <- trackInfoFromWindow.catchAll(_ => UIO.none)
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
    Task.effectAsync { cb =>
      User32.INSTANCE.EnumWindows(
        (window: HWND, _: Pointer) => {
          val windowTitle = fromCString(256)(a => User32.INSTANCE.GetWindowText(window, a, a.length))

          if (windowTitle.contains("[foobar2000]")) {
            cb(UIO.some(windowTitle.replace("[foobar2000]", "").trim))
            false
          } else {
            true
          }
        },
        Pointer.NULL
      )

      cb(UIO.none)

    }
}

object Foobar2000Command extends CommandPlugin[Foobar2000Command] {

  def make(config: Config): ZManaged[PartialEnv, CommandPluginError, Foobar2000Command] = {
    import commandcenter.config.Decoders.*

    for {
      commandNames  <- config.getManaged[Option[List[String]]]("commandNames")
      foobarPathOpt <- config.getManaged[Option[File]]("foobarPath")
      foobarPath    <- UIO(foobarPathOpt).someOrElseM(findFoobarPath).toManaged_
    } yield Foobar2000Command(commandNames.getOrElse(List("foobar", "fb")), foobarPath)
  }

  private def findFoobarPath: ZIO[System, CommandPluginError, File] =
    system
      .env("ProgramFiles(x86)")
      .map(_.map(pf => Paths.get(pf).resolve("foobar2000/foobar2000.exe").toFile).filter(_.exists))
      .some
      .orElse {
        system
          .env("ProgramFiles")
          .map(_.map(pf => Paths.get(pf).resolve("foobar2000/foobar2000.exe").toFile).filter(_.exists))
          .some
      }
      .optional
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

package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.CCRuntime.{ Env, PartialEnv }
import commandcenter.command.Foobar2000Command.Opt
import commandcenter.util.OS
import commandcenter.view.DefaultView
import zio.process.{ Command => PCommand }
import zio.system.System
import zio.{ system, RManaged, UIO, ZIO, ZManaged }

import java.io.File
import java.nio.file.Paths

final case class Foobar2000Command(commandNames: List[String], foobarPath: File) extends Command[Unit] {
  val commandType: CommandType = CommandType.Foobar2000Command
  val title: String            = "foobar2000"

  override val supportedOS: Set[OS] = Set(OS.Windows)

  val playCommand          = decline.Command("play", "Start the current track")(Opts(Opt.Play))
  val pauseCommand         = decline.Command("pause", "Pause the current track")(Opts(Opt.Pause))
  val stopCommand          = decline.Command("stop", "Stop the current track")(Opts(Opt.Stop))
  val nextTrackCommand     = decline.Command("next", "Switch to next track")(Opts(Opt.NextTrack))
  val previousTrackCommand = decline.Command("previous", "Switch to previous track")(Opts(Opt.PreviousTrack))
  val rewindCommand        = decline.Command("rewind", "Stop the current track")(Opts(Opt.Rewind))
  val deleteTrackCommand   = decline.Command("delete", "Delete the current track")(Opts(Opt.DeleteTrack))
  val helpCommand          = decline.Command("help", "Display command usage")(Opts(Opt.Help))

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
      input   <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed   = foobarCommand.parse(input.args)
      message <- ZIO
                   .fromEither(parsed)
                   .foldM(
                     help => UIO(HelpMessage.formatted(help)),
                     {
                       case Some(Opt.Play)          => UIO(fansi.Str(playCommand.header))
                       case Some(Opt.Pause)         => UIO(fansi.Str(pauseCommand.header))
                       case Some(Opt.Stop)          => UIO(fansi.Str(stopCommand.header))
                       case Some(Opt.NextTrack)     => UIO(fansi.Str(nextTrackCommand.header))
                       case Some(Opt.PreviousTrack) => UIO(fansi.Str(previousTrackCommand.header))
                       case Some(Opt.Rewind)        => UIO(fansi.Str("Rewind current track to the beginning"))
                       case Some(Opt.DeleteTrack)   => UIO(fansi.Str(deleteTrackCommand.header))
                       case Some(Opt.Help)          => UIO(fansi.Str(foobarCommand.showHelp))
                       case Some(Opt.Show) | None   => UIO(fansi.Str("Show foobar2000 window"))
                     }
                   )
    } yield {
      val run = for {
        opt <- ZIO.fromEither(parsed).bimap(RunError.CliError, _.getOrElse(Opt.Show))
        _   <- opt match {
                 case Opt.Play          => PCommand(foobarPath.getCanonicalPath, "/play").successfulExitCode
                 case Opt.Pause         => PCommand(foobarPath.getCanonicalPath, "/pause").successfulExitCode
                 case Opt.Stop          => PCommand(foobarPath.getCanonicalPath, "/stop").successfulExitCode
                 case Opt.NextTrack     => PCommand(foobarPath.getCanonicalPath, "/next").successfulExitCode
                 case Opt.PreviousTrack => PCommand(foobarPath.getCanonicalPath, "/prev").successfulExitCode
                 case Opt.Rewind        =>
                   PCommand(foobarPath.getCanonicalPath, "/stop").successfulExitCode *> PCommand(
                     foobarPath.getCanonicalPath,
                     "/play"
                   ).successfulExitCode
                 case Opt.DeleteTrack   =>
                   PCommand(
                     foobarPath.getCanonicalPath,
                     "/playing_command:File Operations/Delete file(s)"
                   ).successfulExitCode
                 case Opt.Show          =>
                   PCommand(foobarPath.getCanonicalPath, "/show").successfulExitCode
                 case Opt.Help          => ZIO.unit
               }
      } yield ()

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.high(input.context))
          .view(DefaultView(title, message))
      )
    }
}

object Foobar2000Command extends CommandPlugin[Foobar2000Command] {
  def make(config: Config): RManaged[PartialEnv, Foobar2000Command] = {
    import commandcenter.config.Decoders._

    for {
      commandNames  <- ZManaged.fromEither(config.get[Option[List[String]]]("commandNames"))
      foobarPathOpt <- ZManaged
                         .fromEither(config.get[Option[File]]("foobarPath"))
      foobarPath    <- UIO(foobarPathOpt).someOrElseM(findFoobarPath).toManaged_
    } yield Foobar2000Command(commandNames.getOrElse(List("foobar", "fb")), foobarPath)
  }

  private def findFoobarPath: ZIO[System, Exception, File] =
    system
      .env("ProgramFiles(x86)")
      .map(_.map(pf => Paths.get(pf).resolve("foobar2000/foobar2000.exe")))
      .some
      .orElse {
        system.env("ProgramFiles").map(_.map(pf => Paths.get(pf).resolve("foobar2000/foobar2000.exe"))).some
      }
      .optional
      .someOrFail(new Exception("Path to foobar2000 not found"))
      .map(_.toFile)

  sealed trait Opt
  case object Opt {
    case object Play          extends Opt
    case object Pause         extends Opt
    case object Stop          extends Opt
    case object NextTrack     extends Opt
    case object PreviousTrack extends Opt
    case object Rewind        extends Opt
    case object DeleteTrack   extends Opt
    case object Show          extends Opt
    case object Help          extends Opt
  }
}

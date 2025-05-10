package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.cache.ZCache
import commandcenter.command.ITunesCommand.Opt
import commandcenter.util.{AppleScript, OS}
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.{Color, Str}
import zio.*

import scala.io.Source

final case class ITunesCommand(commandNames: List[String], cache: ZCache[String, String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.ITunesCommand
  val title: String = "iTunes"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  val rating =
    Opts.argument[Int]("rating").validate("Rating must be between 0-5")((0 to 5).contains(_)).map(Opt.Rate.apply)

  val playCommand = decline.Command("play", "Start the current track")(Opts(Opt.Play))
  val pauseCommand = decline.Command("pause", "Pause the current track")(Opts(Opt.Pause))
  val stopCommand = decline.Command("stop", "Stop the current track")(Opts(Opt.Stop))
  val nextTrackCommand = decline.Command("next", "Switch to next track")(Opts(Opt.NextTrack))
  val previousTrackCommand = decline.Command("previous", "Switch to previous track")(Opts(Opt.PreviousTrack))
  val rewindCommand = decline.Command("rewind", "Stop the current track")(Opts(Opt.Rewind))

  // TODO: Make this word with negative numbers too
  val seekCommand = decline
    .Command("seek", "Seek forward/back by specified amount in seconds")(Opts.argument[Int]("seconds"))
    .map(Opt.Seek.apply)
  val rateCommand = decline.Command("rate", "Rate the current track")(rating)
  val deleteTrackCommand = decline.Command("delete", "Delete the current track")(Opts(Opt.DeleteTrack))
  val helpCommand = decline.Command("help", "Display command usage")(Opts(Opt.Help))
  // TODO: volume control + mute

  val opts = Opts.subcommand(playCommand) orElse
    Opts.subcommand(pauseCommand) orElse
    Opts.subcommand(stopCommand) orElse
    Opts.subcommand(nextTrackCommand) orElse
    Opts.subcommand(previousTrackCommand) orElse
    Opts.subcommand(rewindCommand) orElse
    Opts.subcommand(seekCommand) orElse
    Opts.subcommand(rateCommand) orElse
    Opts.subcommand(deleteTrackCommand) orElse
    Opts.subcommand(helpCommand) orNone

  val itunesCommand = decline.Command("itunes", "iTunes commands")(opts)

  val playFn = AppleScript.loadFunction0(cache)("itunes/play.applescript")
  val pauseFn = AppleScript.loadFunction0(cache)("itunes/pause.applescript")
  val stopFn = AppleScript.loadFunction0(cache)("itunes/stop.applescript")
  val nextTrackFn = AppleScript.loadFunction0(cache)("itunes/next-track.applescript")
  val previousTrackFn = AppleScript.loadFunction0(cache)("itunes/previous-track.applescript")
  val trackDetailsFn = AppleScript.loadFunction0(cache)("itunes/track-details.applescript")
  val seekFn = AppleScript.loadFunction1[Int](cache)("itunes/seek.applescript")
  val rateTrackFn = AppleScript.loadFunction1[Int](cache)("itunes/rate.applescript")
  val deleteTrackFn = AppleScript.loadFunction0(cache)("itunes/delete-track.applescript")

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed = itunesCommand.parse(input.args)
      message <- ZIO
                   .fromEither(parsed)
                   .foldZIO(
                     help => ZIO.succeed(HelpMessage.formatted(help)),
                     {
                       case Some(Opt.Play)          => ZIO.succeed(Str(playCommand.header))
                       case Some(Opt.Pause)         => ZIO.succeed(Str(pauseCommand.header))
                       case Some(Opt.Stop)          => ZIO.succeed(Str(stopCommand.header))
                       case Some(Opt.NextTrack)     => ZIO.succeed(Str(nextTrackCommand.header))
                       case Some(Opt.PreviousTrack) => ZIO.succeed(Str(previousTrackCommand.header))
                       case Some(Opt.Rewind)        => ZIO.succeed(Str("Rewind current track to the beginning"))
                       case Some(Opt.Seek(seconds)) if seconds >= 0 =>
                         ZIO.succeed(Str(s"Skip forward by $seconds seconds"))
                       case Some(Opt.Seek(seconds)) => ZIO.succeed(Str(s"Skip backward by $seconds seconds"))
                       case Some(Opt.Rate(rating))  => ZIO.succeed(Str(s"Rate track $rating stars"))
                       case Some(Opt.DeleteTrack)   => ZIO.succeed(Str(deleteTrackCommand.header))
                       case Some(Opt.Help)          => ZIO.succeed(Str(itunesCommand.showHelp))
                       case None                    =>
                         // TODO: Always show track details at top for every command. May want to also cache this?
                         (for {
                           details <- trackDetailsFn
                           detailsFormatted = details.trim.split("\t") match {
                                                case Array(trackName, artist, album, rating) =>
                                                  Color.Magenta(trackName) ++ " " ++ artist ++ " " ++
                                                    Color.Cyan(album) ++ " " ++ Color.Yellow(rating)

                                                case _ => Str("not playing")
                                              }
                         } yield detailsFormatted).mapError(CommandError.UnexpectedError(this))
                     }
                   )
    } yield {
      val run = for {
        opt <-
          ZIO.fromEither(parsed).orElseFail(RunError.Ignore).someOrFail(RunError.InternalError("No subcommand"))
        _ <- opt match {
               case Opt.Play          => playFn
               case Opt.Pause         => pauseFn
               case Opt.Stop          => stopFn
               case Opt.NextTrack     => nextTrackFn
               case Opt.PreviousTrack => previousTrackFn
               case Opt.Rewind        => seekFn(-100000) // TODO: Is there a nicer way to do this?
               case Opt.Seek(seconds) => seekFn(seconds)
               case Opt.Rate(n)       => rateTrackFn(n)
               case Opt.DeleteTrack   => deleteTrackFn
               case Opt.Help          => ZIO.unit
             }
      } yield ()

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.veryHigh(input.context))
          .rendered(Renderer.renderDefault(title, message))
      )
    }
}

object ITunesCommand extends CommandPlugin[ITunesCommand] {

  def make(config: Config): IO[CommandPluginError, ITunesCommand] =
    for {
      runtime <- ZIO.runtime[Any]
      cache = ZCache
                .memoizeZIO(1024, None)((resource: String) =>
                  ZIO.succeed(Some(Source.fromResource(resource)).map(_.mkString))
                )(runtime)
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
    } yield ITunesCommand(commandNames.getOrElse(List("itunes")), cache)

  sealed trait Opt

  case object Opt {
    case object Play extends Opt
    case object Pause extends Opt
    case object Stop extends Opt
    case object NextTrack extends Opt
    case object PreviousTrack extends Opt
    case object Rewind extends Opt
    final case class Seek(seconds: Int) extends Opt
    final case class Rate(rating: Int) extends Opt
    case object DeleteTrack extends Opt
    case object Help extends Opt
  }
}

package commandcenter.command

import com.monovore.decline
import com.monovore.decline.Opts
import commandcenter.CCRuntime.Env
import commandcenter.CommandContext
import commandcenter.command.ITunesCommand.Opt
import commandcenter.util.{ AppleScript, OS, TTS }
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ UIO, ZIO }

final case class ITunesCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.ITunesCommand

  val commandNames: List[String] = List("itunes")

  val title: String = "iTunes"

  override val supportedOS: Set[OS] = Set(OS.MacOS)

  val rating =
    Opts.argument[Int]("rating").validate("Rating must be between 0-5")((0 to 5).contains(_)).map(Opt.Rate)

  val playCommand          = decline.Command("play", "Start the current track")(Opts(Opt.Play))
  val pauseCommand         = decline.Command("pause", "Pause the current track")(Opts(Opt.Pause))
  val stopCommand          = decline.Command("stop", "Stop the current track")(Opts(Opt.Stop))
  val nextTrackCommand     = decline.Command("next", "Switch to next track")(Opts(Opt.NextTrack))
  val previousTrackCommand = decline.Command("previous", "Switch to previous track")(Opts(Opt.PreviousTrack))
  val rewindCommand        = decline.Command("rewind", "Stop the current track")(Opts(Opt.Rewind))

  // TODO: Make this word with negative numbers too
  val seekCommand = decline
    .Command("seek", "Seek forward/back by specified amount in seconds")(Opts.argument[Int]("seconds"))
    .map(Opt.Seek)
  val rateCommand        = decline.Command("rate", "Rate the current track")(rating)
  val deleteTrackCommand = decline.Command("delete", "Delete the current track")(Opts(Opt.DeleteTrack))
  val helpCommand        = decline.Command("help", "Display command usage")(Opts(Opt.Help))
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

  val playFn          = AppleScript.loadFunction0("applescript/itunes/play.applescript")
  val pauseFn         = AppleScript.loadFunction0("applescript/itunes/pause.applescript")
  val stopFn          = AppleScript.loadFunction0("applescript/itunes/stop.applescript")
  val nextTrackFn     = AppleScript.loadFunction0("applescript/itunes/next-track.applescript")
  val previousTrackFn = AppleScript.loadFunction0("applescript/itunes/previous-track.applescript")
  val trackDetailsFn  = AppleScript.loadFunction0("applescript/itunes/track-details.applescript")
  val seekFn          = AppleScript.loadFunction1[Int]("applescript/itunes/seek.applescript")
  val rateTrackFn     = AppleScript.loadFunction1[Int]("applescript/itunes/rate.applescript")
  val deleteTrackFn   = AppleScript.loadFunction0("applescript/itunes/delete-track.applescript")

  override def argsPreview(
    args: List[String],
    context: CommandContext
  ): ZIO[Env, CommandError, List[PreviewResult[Unit]]] = {
    val parsed = itunesCommand.parse(args)

    for {
      message <- ZIO
                  .fromEither(parsed)
                  .foldM(
                    help => UIO(HelpMessage.formatted(help)), {
                      case Some(Opt.Play)          => UIO(fansi.Str(playCommand.header))
                      case Some(Opt.Pause)         => UIO(fansi.Str(pauseCommand.header))
                      case Some(Opt.Stop)          => UIO(fansi.Str(stopCommand.header))
                      case Some(Opt.NextTrack)     => UIO(fansi.Str(nextTrackCommand.header))
                      case Some(Opt.PreviousTrack) => UIO(fansi.Str(previousTrackCommand.header))
                      case Some(Opt.Rewind)        => UIO(fansi.Str("Rewind current track to the beginning"))
                      case Some(Opt.Seek(seconds)) if seconds >= 0 =>
                        UIO(fansi.Str(s"Skip forward by $seconds seconds"))
                      case Some(Opt.Seek(seconds)) => UIO(fansi.Str(s"Skip backward by $seconds seconds"))
                      case Some(Opt.Rate(rating))  => UIO(fansi.Str(s"Rate track ${rating} stars"))
                      case Some(Opt.DeleteTrack)   => UIO(fansi.Str(deleteTrackCommand.header))
                      case Some(Opt.Help)          => UIO(fansi.Str(itunesCommand.showHelp))
                      case None =>
                        (for {
                          details                                 <- trackDetailsFn
                          Array(trackName, artist, album, rating) = details.trim.split("\t")
                        } yield fansi.Color.Magenta(trackName) ++ " " ++ artist ++ " " ++ fansi.Color
                          .Yellow(album) ++ " " ++ fansi.Color.Yellow(rating))
                          .mapError(
                            CommandError.UnexpectedException
                          ) // TODO: Always show track details at top for every command. May want to also cache this?
                    }
                  )
    } yield {
      val run = for {
        opt <- ZIO.fromEither(parsed).some
        result <- opt match {
                   case Opt.Play          => playFn
                   case Opt.Pause         => pauseFn
                   case Opt.Stop          => stopFn
                   case Opt.NextTrack     => nextTrackFn
                   case Opt.PreviousTrack => previousTrackFn
                   case Opt.Rewind        => seekFn(-100000) // TODO: Is there a nicer way to do this?
                   case Opt.Seek(seconds) => TTS.say(seconds.toString) *> seekFn(seconds)
                   case Opt.Rate(n)       => rateTrackFn(n)
                   case Opt.DeleteTrack   => deleteTrackFn
                   case Opt.Help          => ZIO.unit
                 }
      } yield result

      List(
        Preview.unit
          .onRun(run.ignore)
          .score(Scores.high(context))
          .view(DefaultView(title, message))
      )
    }
  }
}

object ITunesCommand extends CommandPlugin[ITunesCommand] {
  implicit val decoder: Decoder[ITunesCommand] = Decoder.const(ITunesCommand())

  sealed trait Opt

  case object Opt {

    case object Play                    extends Opt
    case object Pause                   extends Opt
    case object Stop                    extends Opt
    case object NextTrack               extends Opt
    case object PreviousTrack           extends Opt
    case object Rewind                  extends Opt
    final case class Seek(seconds: Int) extends Opt
    final case class Rate(rating: Int)  extends Opt
    case object DeleteTrack             extends Opt
    case object Help                    extends Opt

  }
}

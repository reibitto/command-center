package commandcenter.command

import cats.implicits.*
import com.monovore.decline
import com.monovore.decline.{Help, Opts}
import com.typesafe.config.Config
import commandcenter.command.win.DisplayOutputs
import commandcenter.command.win.DisplayOutputs.SwitchStrategy
import commandcenter.command.DisplaySwitchCommand.{DisplayEntry, DisplaySubcommand}
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts.Shortcuts
import commandcenter.util.OS
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.{Back, Str}
import io.circe.Decoder
import zio.*

/** Switches which physical display output (e.g. HDMI1/TV, DisplayPort2/monitor,
  * DisplayPort3/projector) is active, via the Windows display-config (CCD) API.
  * See [[commandcenter.command.win.DisplayOutputs]].
  */
final case class DisplaySwitchCommand(commandNames: List[String], displays: List[DisplayEntry]) extends Command[Unit] {
  val commandType: CommandType = CommandType.DisplaySwitchCommand
  val title: String = "Switch Display"

  override val supportedOS: Set[OS] = Set(OS.Windows)

  val nextOpt: Opts[Boolean] =
    Opts
      .flag("next", "Force rotating to the next GPU output pipeline for this display, bypassing the remembered one")
      .orFalse

  val extendOpt: Opts[Boolean] =
    Opts
      .flag(
        "extend",
        "Use the extend/set-primary/narrow sequence instead of the default single direct SetDisplayConfig call"
      )
      .orFalse

  val switchCommand: decline.Command[(String, Boolean, Boolean)] =
    decline.Command("switch", "Activate only the named display, deactivating the others")(
      (Opts.argument[String]("name"), nextOpt, extendOpt).tupled
    )

  val listCommand: decline.Command[DisplaySubcommand] =
    decline.Command("list", "List every display Windows currently knows about")(Opts(DisplaySubcommand.List))

  val infoCommand: decline.Command[DisplaySubcommand] =
    decline.Command("info", "Show current resolution, refresh rate and display scale for each active display")(
      Opts(DisplaySubcommand.Info)
    )

  val helpCommand: decline.Command[DisplaySubcommand] =
    decline.Command("help", "Display usage help")(Opts(DisplaySubcommand.Help))

  val opts: Opts[DisplaySubcommand] =
    Opts.subcommand(switchCommand).map { case (name, next, extend) =>
      DisplaySubcommand.Switch(name, next, extend)
    } orElse
      Opts.subcommand(listCommand) orElse
      Opts.subcommand(infoCommand) orElse
      Opts.subcommand(helpCommand) withDefault DisplaySubcommand.Help

  val displaySwitchCommand: decline.Command[DisplaySubcommand] = decline.Command("display", title)(opts)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed = displaySwitchCommand.parse(input.args)
      result <- ZIO
                  .fromEither(parsed)
                  .foldZIO(
                    h => ZIO.succeed(PreviewResults.one(Preview.help(h).score(Scores.veryHigh(input.context)))),
                    {
                      case DisplaySubcommand.Help =>
                        ZIO.succeed(
                          PreviewResults.one(
                            Preview
                              .help(Help.fromCommand(displaySwitchCommand))
                              .score(Scores.veryHigh(input.context))
                          )
                        )

                      case DisplaySubcommand.List =>
                        DisplayOutputs
                          .listPaths()
                          .mapBoth(
                            CommandError.UnexpectedError(this),
                            paths => {
                              // QDC_ALL_PATHS also returns every hypothetical source/target pairing the GPU
                              // driver supports (mostly with no friendly name) - not real displays, just noise.
                              // It also lists the same physical display once per source/GPU-pipeline it could be
                              // assigned to - collapse those down to one row per display, preferring whichever
                              // duplicate is currently active (or available) as the representative one to show.
                              val displaysByTarget = paths
                                .filter(_.friendlyName.nonEmpty)
                                .groupBy(_.targetId)
                                .values
                                .map(group =>
                                  group.find(_.active).orElse(group.find(_.available)).getOrElse(group.head)
                                )
                                .toList
                                .sortBy(_.friendlyName)

                              if (displaysByTarget.isEmpty)
                                PreviewResults.one(
                                  Preview.unit
                                    .rendered(Renderer.renderDefault(title, "No displays found."))
                                    .score(Scores.veryHigh(input.context))
                                )
                              else
                                PreviewResults.fromIterable(displaysByTarget.zipWithIndex.map { case (p, i) =>
                                  val matchingEntry =
                                    displays.find(e => p.friendlyName.toLowerCase.contains(e.matches.toLowerCase))
                                  val label = matchingEntry.map(_.name).getOrElse(p.friendlyName)
                                  val resolution = matchingEntry.flatMap(_.resolution)
                                  val refreshRateHz = matchingEntry.flatMap(_.refreshRateHz)
                                  val indicator =
                                    if (p.active) Back.Green(" ")
                                    else if (p.available) Back.Red(" ")
                                    else Back.DarkGray(" ")
                                  val rendered = indicator ++ Str(s" $label (${p.friendlyName})")

                                  Preview.unit
                                    .onRun(
                                      DisplayOutputs
                                        .activateOnly(
                                          p.friendlyName,
                                          resolution = resolution,
                                          refreshRateHz = refreshRateHz
                                        )
                                        .orDie
                                    )
                                    .rendered(Renderer.renderDefault(title, rendered))
                                    .score(Scores.veryHigh(input.context) - i * 1e-6)
                                })
                            }
                          )

                      case DisplaySubcommand.Info =>
                        DisplayOutputs
                          .listPaths()
                          .mapError(CommandError.UnexpectedError(this))
                          .flatMap { paths =>
                            val displaysByTarget = paths
                              .filter(_.friendlyName.nonEmpty)
                              .groupBy(_.targetId)
                              .values
                              .map(group => group.find(_.active).orElse(group.find(_.available)).getOrElse(group.head))
                              .toList
                              .sortBy(_.friendlyName)

                            if (displaysByTarget.isEmpty)
                              ZIO.succeed(
                                PreviewResults.one(
                                  Preview.unit
                                    .rendered(Renderer.renderDefault(title, "No displays found."))
                                    .score(Scores.veryHigh(input.context))
                                )
                              )
                            else
                              ZIO
                                .foreach(displaysByTarget.zipWithIndex) { case (p, i) =>
                                  val matchingEntry =
                                    displays.find(e => p.friendlyName.toLowerCase.contains(e.matches.toLowerCase))
                                  val label = matchingEntry.map(_.name).getOrElse(p.friendlyName)

                                  val statusZIO =
                                    if (!p.active)
                                      ZIO.succeed(if (p.available) "off" else "unavailable")
                                    else
                                      DisplayOutputs
                                        .currentModeInfo(p.devicePath)
                                        .map {
                                          case Some(m) =>
                                            s"${m.width}x${m.height} @ ${m.refreshRateHz}Hz, ${m.scalePercent}% scale"
                                          case None => "active (mode unknown)"
                                        }
                                        .catchAll(t => ZIO.succeed(s"active (could not read mode: ${t.getMessage})"))

                                  statusZIO.map { status =>
                                    val indicator =
                                      if (p.active) Back.Green(" ")
                                      else if (p.available) Back.Red(" ")
                                      else Back.DarkGray(" ")
                                    val rendered = indicator ++ Str(s" $label ($status)")

                                    Preview.unit
                                      .rendered(Renderer.renderDefault(title, rendered))
                                      .score(Scores.veryHigh(input.context) - i * 1e-6)
                                  }
                                }
                                .map(PreviewResults.fromIterable)
                          }

                      case DisplaySubcommand.Switch(name, next, extend) =>
                        displays.find(_.name.equalsIgnoreCase(name)) match {
                          case None =>
                            ZIO.succeed(
                              PreviewResults.one(
                                Preview.unit
                                  .rendered(
                                    Renderer.renderDefault(
                                      title,
                                      s"Unknown display `$name`. Configured: ${displays.map(_.name).mkString(", ")}"
                                    )
                                  )
                                  .score(Scores.veryHigh(input.context))
                              )
                            )

                          case Some(entry) =>
                            val strategy = if (extend) SwitchStrategy.ExtendSetPrimaryNarrow else SwitchStrategy.Direct
                            val suffix =
                              (if (next) " (next pipeline)" else "") + (if (extend) " (extend)" else "")

                            ZIO.succeed(
                              PreviewResults.one(
                                Preview.unit
                                  .onRun(
                                    DisplayOutputs
                                      .activateOnly(
                                        entry.matches,
                                        next = next,
                                        resolution = entry.resolution,
                                        refreshRateHz = entry.refreshRateHz,
                                        strategy = strategy
                                      )
                                      .orDie
                                  )
                                  .rendered(Renderer.renderDefault(title, s"Switch to ${entry.name}$suffix"))
                                  .score(Scores.veryHigh(input.context))
                              )
                            )
                        }
                    }
                  )
    } yield result
}

object DisplaySwitchCommand extends CommandPlugin[DisplaySwitchCommand] {

  final case class DisplayEntry(
      name: String,
      matches: String,
      shortcut: Option[KeyboardShortcut],
      resolution: Option[(Int, Int)],
      refreshRateHz: Option[Int]
  )

  object DisplayEntry {

    private val ResolutionPattern = """(\d+)\s*[xX]\s*(\d+)""".r

    implicit val decoder: Decoder[DisplayEntry] = Decoder.instance { c =>
      for {
        name          <- c.get[String]("name")
        matches       <- c.get[String]("match")
        shortcut      <- c.get[Option[KeyboardShortcut]]("shortcut")
        resolutionStr <- c.get[Option[String]]("resolution")
        resolution    <- resolutionStr match {
                        case None                          => Right(None)
                        case Some(ResolutionPattern(w, h)) => Right(Some((w.toInt, h.toInt)))
                        case Some(other)                   =>
                          Left(
                            io.circe.DecodingFailure(
                              s"Invalid `resolution` value `$other`, expected e.g. `3840x2160`",
                              c.history
                            )
                          )
                      }
        refreshRateHz <- c.get[Option[Int]]("refreshRateHz")
      } yield DisplayEntry(name, matches, shortcut, resolution, refreshRateHz)
    }
  }

  sealed trait DisplaySubcommand

  object DisplaySubcommand {
    final case class Switch(name: String, next: Boolean, extend: Boolean) extends DisplaySubcommand
    case object List extends DisplaySubcommand
    case object Info extends DisplaySubcommand
    case object Help extends DisplaySubcommand
  }

  def make(config: Config): ZIO[Env, CommandPluginError, DisplaySwitchCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
      displays     <- config.getZIO[Option[List[DisplayEntry]]]("displays").map(_.getOrElse(Nil))
      _            <- ZIO
             .foreach(displays.flatMap(entry => entry.shortcut.map(entry -> _))) { case (entry, shortcut) =>
               Shortcuts.addGlobalShortcut(shortcut)(_ =>
                 DisplayOutputs
                   .activateOnly(entry.matches, resolution = entry.resolution, refreshRateHz = entry.refreshRateHz)
                   .tapErrorCause(t => ZIO.logWarningCause(s"Error switching to display `${entry.name}`", t))
                   .ignore
               )
             }
             .mapError(CommandPluginError.UnexpectedException.apply)
    } yield DisplaySwitchCommand(commandNames.getOrElse(List("display")), displays)
}

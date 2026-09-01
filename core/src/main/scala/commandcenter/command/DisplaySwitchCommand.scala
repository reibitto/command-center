package commandcenter.command

import cats.implicits.*
import com.monovore.decline
import com.monovore.decline.{Help, Opts}
import com.typesafe.config.Config
import commandcenter.command.win.DisplayOutputs
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

  val switchCommand: decline.Command[(String, Boolean)] =
    decline.Command("switch", "Activate only the named display, deactivating the others")(
      (Opts.argument[String]("name"), nextOpt).tupled
    )

  val listCommand: decline.Command[DisplaySubcommand] =
    decline.Command("list", "List every display Windows currently knows about")(Opts(DisplaySubcommand.List))

  val helpCommand: decline.Command[DisplaySubcommand] =
    decline.Command("help", "Display usage help")(Opts(DisplaySubcommand.Help))

  val opts: Opts[DisplaySubcommand] =
    Opts.subcommand(switchCommand).map { case (name, next) => DisplaySubcommand.Switch(name, next) } orElse
      Opts.subcommand(listCommand) orElse
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
                                  val refreshRateHz = matchingEntry.flatMap(_.refreshRateHz)
                                  val indicator =
                                    if (p.active) Back.Green(" ")
                                    else if (p.available) Back.Red(" ")
                                    else Back.DarkGray(" ")
                                  val rendered = indicator ++ Str(s" $label (${p.friendlyName})")

                                  Preview.unit
                                    .onRun(
                                      DisplayOutputs.activateOnly(p.friendlyName, refreshRateHz = refreshRateHz).orDie
                                    )
                                    .rendered(Renderer.renderDefault(title, rendered))
                                    .score(Scores.veryHigh(input.context) - i * 1e-6)
                                })
                            }
                          )

                      case DisplaySubcommand.Switch(name, next) =>
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
                            ZIO.succeed(
                              PreviewResults.one(
                                Preview.unit
                                  .onRun(
                                    DisplayOutputs
                                      .activateOnly(entry.matches, next = next, refreshRateHz = entry.refreshRateHz)
                                      .orDie
                                  )
                                  .rendered(
                                    Renderer.renderDefault(
                                      title,
                                      if (next) s"Switch to ${entry.name} (next pipeline)"
                                      else s"Switch to ${entry.name}"
                                    )
                                  )
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
      refreshRateHz: Option[Int]
  )

  object DisplayEntry {

    implicit val decoder: Decoder[DisplayEntry] = Decoder.instance { c =>
      for {
        name          <- c.get[String]("name")
        matches       <- c.get[String]("match")
        shortcut      <- c.get[Option[KeyboardShortcut]]("shortcut")
        refreshRateHz <- c.get[Option[Int]]("refreshRateHz")
      } yield DisplayEntry(name, matches, shortcut, refreshRateHz)
    }
  }

  sealed trait DisplaySubcommand

  object DisplaySubcommand {
    final case class Switch(name: String, next: Boolean) extends DisplaySubcommand
    case object List extends DisplaySubcommand
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
                   .activateOnly(entry.matches, refreshRateHz = entry.refreshRateHz)
                   .tapErrorCause(t => ZIO.logWarningCause(s"Error switching to display `${entry.name}`", t))
                   .ignore
               )
             }
             .mapError(CommandPluginError.UnexpectedException.apply)
    } yield DisplaySwitchCommand(commandNames.getOrElse(List("display")), displays)
}

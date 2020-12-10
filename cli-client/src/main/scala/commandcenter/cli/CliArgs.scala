package commandcenter.cli

import com.monovore.decline
import com.monovore.decline.{ Command, Opts }

object CliArgs {
  val headlessCommand =
    decline.Command("headless", "Run in headless mode waiting to receive requests")(Opts(CliCommand.Headless))
  val versionCommand  = decline.Command("version", "Print the current version")(Opts(CliCommand.Version))
  val helpCommand     = decline.Command("help", "Display usage help")(Opts(CliCommand.Help))

  val opts: Opts[CliCommand] =
    Opts.subcommand(headlessCommand) orElse
      Opts.subcommand(versionCommand) orElse
      Opts.subcommand(helpCommand) withDefault CliCommand.Standalone

  val rootCommand: Command[CliCommand] = decline.Command("commandcenter", "Command Center commands")(opts)
}

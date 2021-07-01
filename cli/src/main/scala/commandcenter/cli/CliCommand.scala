package commandcenter.cli

sealed trait CliCommand

object CliCommand {
  case object Standalone extends CliCommand
  case object Help       extends CliCommand
  case object Version    extends CliCommand
}

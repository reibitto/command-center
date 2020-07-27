package commandcenter.command

sealed trait CommandResult

object CommandResult {

  case object Exit extends CommandResult

}

package commandcenter.command

import commandcenter.CommandContext

trait CommandInput
object CommandInput {
  final case class Args(commandName: String, args: List[String], context: CommandContext) extends CommandInput
  final case class Prefixed(prefix: String, rest: String, context: CommandContext)        extends CommandInput
  final case class Keyword(keyword: String, context: CommandContext)                      extends CommandInput
}

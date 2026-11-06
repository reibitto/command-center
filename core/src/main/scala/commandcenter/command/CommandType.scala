package commandcenter.command

import commandcenter.command
import enumeratum.*

sealed trait CommandType extends EnumEntry

object CommandType extends Enum[CommandType] {

  // By-name to avoid an init cycle, since each companion refers back to its CommandType
  sealed abstract class BuiltIn(plugin0: => CommandPlugin[? <: Command[?]]) extends CommandType {
    def plugin: CommandPlugin[? <: Command[?]] = plugin0
  }

  case object BorderlessCommand extends BuiltIn(command.BorderlessCommand)
  case object CalculatorCommand extends BuiltIn(command.CalculatorCommand)
  case object ChessCommand extends BuiltIn(command.ChessCommand)
  case object ConfigCommand extends BuiltIn(command.ConfigCommand)
  case object DecodeBase64Command extends BuiltIn(command.DecodeBase64Command)
  case object DecodeUrlCommand extends BuiltIn(command.DecodeUrlCommand)
  case object DisplaySwitchCommand extends BuiltIn(command.DisplaySwitchCommand)
  case object EncodeBase64Command extends BuiltIn(command.EncodeBase64Command)
  case object EncodeUrlCommand extends BuiltIn(command.EncodeUrlCommand)
  case object EpochMillisCommand extends BuiltIn(command.EpochMillisCommand)
  case object EpochUnixCommand extends BuiltIn(command.EpochUnixCommand)
  case object ExitCommand extends BuiltIn(command.ExitCommand)
  case object ExternalIPCommand extends BuiltIn(command.ExternalIPCommand)
  case object FileNavigationCommand extends BuiltIn(command.FileNavigationCommand)
  case object FindFileCommand extends BuiltIn(command.FindFileCommand)
  case object FindInFileCommand extends BuiltIn(command.FindInFileCommand)
  case object Foobar2000Command extends BuiltIn(command.Foobar2000Command)
  case object HashCommand extends BuiltIn(command.HashCommand)
  case object HoogleCommand extends BuiltIn(command.HoogleCommand)
  case object HttpCommand extends BuiltIn(command.HttpCommand)
  case object ITunesCommand extends BuiltIn(command.ITunesCommand)
  case object KeepSoundCommand extends BuiltIn(command.KeepSoundCommand)
  case object LocalIPCommand extends BuiltIn(command.LocalIPCommand)
  case object LockCommand extends BuiltIn(command.LockCommand)
  case object LoremIpsumCommand extends BuiltIn(command.LoremIpsumCommand)
  case object MuteCommand extends BuiltIn(command.MuteCommand)
  case object OpacityCommand extends BuiltIn(command.OpacityCommand)
  case object OpenBrowserCommand extends BuiltIn(command.OpenBrowserCommand)
  case object ProcessIdCommand extends BuiltIn(command.ProcessIdCommand)
  case object RadixCommand extends BuiltIn(command.RadixCommand)
  case object RebootCommand extends BuiltIn(command.RebootCommand)
  case object ReloadCommand extends BuiltIn(command.ReloadCommand)
  case object ResizeCommand extends BuiltIn(command.ResizeCommand)
  case object SearchCratesCommand extends BuiltIn(command.SearchCratesCommand)
  case object SearchMavenCommand extends BuiltIn(command.SearchMavenCommand)
  case object SearchUrlCommand extends BuiltIn(command.SearchUrlCommand)
  case object ShellCommand extends BuiltIn(command.ShellCommand)
  case object SnippetsCommand extends BuiltIn(command.SnippetsCommand)
  case object SpeakCommand extends BuiltIn(command.SpeakCommand)
  case object SuspendProcessCommand extends BuiltIn(command.SuspendProcessCommand)
  case object SwitchWindowCommand extends BuiltIn(command.SwitchWindowCommand)
  case object SystemCommand extends BuiltIn(command.SystemCommand)
  case object UnitConversionCommand extends BuiltIn(command.UnitConversionCommand)
  case object TerminalCommand extends BuiltIn(command.TerminalCommand)
  case object TimerCommand extends BuiltIn(command.TimerCommand)
  case object ToggleDesktopIconsCommand extends BuiltIn(command.ToggleDesktopIconsCommand)
  case object ToggleHiddenFilesCommand extends BuiltIn(command.ToggleHiddenFilesCommand)
  case object UUIDCommand extends BuiltIn(command.UUIDCommand)
  case object WorldTimesCommand extends BuiltIn(command.WorldTimesCommand)

  final case class External(typeName: String) extends CommandType

  object External {

    def of[T](commandClass: Class[T]): CommandType.External =
      External(commandClass.getCanonicalName)
  }

  lazy val values: IndexedSeq[CommandType] = findValues
}

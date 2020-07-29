package commandcenter.command

import enumeratum._

sealed trait CommandType extends EnumEntry

object CommandType extends Enum[CommandType] {
  case object DecodeBase64Command       extends CommandType
  case object DecodeUrlCommand          extends CommandType
  case object EncodeBase64Command       extends CommandType
  case object EncodeUrlCommand          extends CommandType
  case object EpochMillisCommand        extends CommandType
  case object EpochUnixCommand          extends CommandType
  case object ExitCommand               extends CommandType
  case object ExternalIPCommand         extends CommandType
  case object FileNavigationCommand     extends CommandType
  case object FindFileCommand           extends CommandType
  case object FindInFileCommand         extends CommandType
  case object HashCommand               extends CommandType
  case object ITunesCommand             extends CommandType
  case object LocalIPCommand            extends CommandType
  case object LockCommand               extends CommandType
  case object OpacityCommand            extends CommandType
  case object OpenBrowserCommand        extends CommandType
  case object RadixCommand              extends CommandType
  case object ReloadCommand             extends CommandType
  case object ResizeCommand             extends CommandType
  case object SearchUrlCommand          extends CommandType
  case object SearchMavenCommand        extends CommandType
  case object TemperatureCommand        extends CommandType
  case object TerminalCommand           extends CommandType
  case object TimerCommand              extends CommandType
  case object ToggleDesktopIconsCommand extends CommandType
  case object ToggleHiddenFilesCommand  extends CommandType
  case object UUIDCommand               extends CommandType
  case object WorldTimesCommand         extends CommandType

  case class External(typeName: String) extends CommandType

  override def values: IndexedSeq[CommandType] = findValues
}

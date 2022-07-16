package commandcenter.command

sealed trait RunOption

object RunOption {
  case object Exit extends RunOption
  case object Hide extends RunOption
  case object RemainOpen extends RunOption
}

package commandcenter.ui

sealed trait RenderTrigger

object RenderTrigger {
  case object InputChange  extends RenderTrigger
  case object WindowResize extends RenderTrigger
}

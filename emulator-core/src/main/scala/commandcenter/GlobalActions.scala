package commandcenter

import commandcenter.shortcuts.Shortcuts
import zio.{ RIO, ZIO }

object GlobalActions {
  def setupCommon(actions: Vector[GlobalAction]): RIO[Shortcuts, Unit] =
    ZIO.foreach_(actions) { action =>
      val run = action.id match {
        case GlobalActionId.MinimizeWindow       => WindowManager.minimizeWindow
        case GlobalActionId.MaximizeWindow       => WindowManager.maximizeWindow
        case GlobalActionId.ToggleMaximizeWindow => WindowManager.toggleMaximizeWindow
        case GlobalActionId.CenterWindow         => WindowManager.centerScreen
        case GlobalActionId.MoveToPreviousScreen => WindowManager.moveToPreviousDisplay
        case GlobalActionId.MoveToNextScreen     => WindowManager.moveToNextDisplay
        case GlobalActionId.ResizeToScreenSize   => WindowManager.resizeToScreenSize

        case id @ GlobalActionId.CycleWindowSizeLeft =>
          WindowManager
            .cycleWindowSize(1, id.entryName)(
              Vector(
                WindowBounds(left = 0, top = 0, right = 0.5, bottom = 1.0),
                WindowBounds(left = 0 / 3.0, top = 0, right = 2.0 / 3.0, bottom = 1.0),
                WindowBounds(left = 0 / 3.0, top = 0, right = 1.0 / 3.0, bottom = 1.0)
              )
            )

        case id @ GlobalActionId.CycleWindowSizeRight =>
          WindowManager
            .cycleWindowSize(1, id.entryName)(
              Vector(
                WindowBounds(left = 0.5, top = 0, right = 1.0, bottom = 1.0),
                WindowBounds(left = 1.0 / 3.0, top = 0, right = 1.0, bottom = 1.0),
                WindowBounds(left = 2.0 / 3.0, top = 0, right = 1.0, bottom = 1.0)
              )
            )

        case id @ GlobalActionId.CycleWindowSizeTop =>
          WindowManager
            .cycleWindowSize(1, id.entryName)(
              Vector(
                WindowBounds(left = 0, top = 0, right = 1.0, bottom = 0.5),
                WindowBounds(left = 0, top = 0, right = 1.0, bottom = 2.0 / 3.0),
                WindowBounds(left = 0, top = 0, right = 1.0, bottom = 1.0 / 3.0)
              )
            )

        case id @ GlobalActionId.CycleWindowSizeBottom =>
          WindowManager
            .cycleWindowSize(1, id.entryName)(
              Vector(
                WindowBounds(left = 0, top = 0.5, right = 1.0, bottom = 1.0),
                WindowBounds(left = 0, top = 1.0 / 3.0, right = 1.0, bottom = 1.0),
                WindowBounds(left = 0, top = 2.0 / 3.0, right = 1.0, bottom = 1.0)
              )
            )
      }

      shortcuts.addGlobalShortcut(action.shortcut)(_ => run.ignore)
    }
}

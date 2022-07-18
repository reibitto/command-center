package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools

object CCRuntime {
  type PartialEnv = Tools & Shortcuts
  type Env = PartialEnv & Conf
}

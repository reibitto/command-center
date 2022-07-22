package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.Tools

object CCRuntime {
  type Env = Conf & Tools & Shortcuts & Sttp
}

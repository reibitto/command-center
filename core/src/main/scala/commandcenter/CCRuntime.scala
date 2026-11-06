package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.{Tools, ToolsLive}
import zio.*

object CCRuntime {
  type Env = Conf & Tools & Shortcuts & Sttp

  def layer(terminalType: TerminalType, shortcuts: TaskLayer[Shortcuts]): TaskLayer[Env] =
    Runtime.setExecutor(Executor.makeDefault(autoBlocking = false)) >>>
      Runtime.removeDefaultLoggers >>>
      ZLayer.make[Env](
        ConfigLive.layer,
        shortcuts,
        ToolsLive.make,
        SttpLive.make,
        CCLogging.addLoggerFor(terminalType),
        Runtime.setUnhandledErrorLogLevel(LogLevel.Warning)
      )
}

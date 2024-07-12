package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.ToolsLive
import commandcenter.CCRuntime.Env
import zio.*
import zio.test.{TestEnvironment, ZIOSpec}

import java.util.Locale

trait CommandBaseSpec extends ZIOSpec[TestEnvironment & Env] {

  override def bootstrap: ZLayer[Any, Any, TestEnvironment & Env] =
    zio.test.testEnvironment ++ CommandBaseSpec.testLayer

  val defaultCommandContext: CommandContext =
    CommandContext(Locale.ENGLISH, TestTerminal, 1.0)
}

object CommandBaseSpec {

  val testLayer: ZLayer[Any, Any, Env] = ZLayer.make[Env](
    ConfigFake.layer,
    Shortcuts.unsupported,
    ToolsLive.make,
    SttpLive.make,
    Runtime.removeDefaultLoggers >>> CCLogging.addLoggerFor(TerminalType.Test),
    Runtime.setUnhandledErrorLogLevel(LogLevel.Warning)
  )
}

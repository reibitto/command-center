package commandcenter

import commandcenter.shortcuts.Shortcuts
import commandcenter.tools.ToolsLive
import commandcenter.CCRuntime.Env
import zio.*
import zio.test.{TestEnvironment, ZIOSpec}

import java.util.Locale

trait CommandBaseSpec extends ZIOSpec[TestEnvironment & Env] {

  override def bootstrap: ZLayer[Scope, Any, TestEnvironment & Env] =
    zio.test.testEnvironment ++ CommandBaseSpec.testLayer

//  val testEnv: Layer[Throwable, TestEnv] = {
//    ZLayer.fromMagic[TestPartialEnv](
//      testEnvironment,
//      CCLogging.make(TerminalType.Test),
//      ToolsLive.make.!,
//      Shortcuts.unsupported,
//      HttpClientZioBackend.layer()
//    ) ++ ConfigFake.layer
//  }

  val defaultCommandContext: CommandContext =
    CommandContext(Locale.ENGLISH, TestTerminal, 1.0)

//  override def aspects: List[TestAspect[Nothing, TestEnv, Nothing, Any]] =
//    List(TestAspect.timeoutWarning(60.seconds))

//  override def runner: TestRunner[TestEnv, Any] =
//    TestRunner(TestExecutor.default(testEnv.!))
}

object CommandBaseSpec {

  val testLayer = ZLayer.make[Env](
    ConfigFake.layer,
    Shortcuts.unsupported,
    ToolsLive.make
  )
}

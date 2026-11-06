package commandcenter.util

import zio.test.*

object ScriptTemplateSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] =
    suite("ScriptTemplateSpec")(
      test("AppleScript arguments with quotes and backslashes stay inside the string") {
        assertTrue(
          ScriptTemplate.fill("""display notification "{0}"""", Seq("""say "hi" \ bye"""), AppleScript.escape) ==
            """display notification "say \"hi\" \\ bye""""
        )
      },
      test("PowerShell arguments with single quotes stay inside the string") {
        assertTrue(ScriptTemplate.fill("$t = '{0}'", Seq("don't"), PowerShellScript.escape) == "$t = 'don''t'")
      }
    )
}

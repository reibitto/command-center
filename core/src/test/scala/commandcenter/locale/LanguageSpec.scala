package commandcenter.locale

import java.util.Locale

import zio.test.Assertion._
import zio.test._

object LanguageSpec extends DefaultRunnableSpec {
  def spec =
    suite("LanguageSpec")(
      test("detect plain English text") {
        assert(Language.detect("Some english text."))(equalTo(Locale.ENGLISH))
      },
      test("detect plain Japanese text") {
        assert(Language.detect("日本語"))(equalTo(Locale.JAPANESE))
      },
      test("detect Japanese even if it's mixed with some English words") {
        assert(Language.detect("Do you 日本語?"))(equalTo(Locale.JAPANESE))
      },
      test("detect plain Korean text") {
        assert(Language.detect("한국어"))(equalTo(Locale.KOREAN))
      }
    )
}

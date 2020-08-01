package commandcenter.locale

import java.util.Locale

object Language {
  // TODO: Improve auto-detection and support other languages. This is very naive right now. Things will get more
  // complicated when we have multiple languages that share the same script. Look for good 3rd party libraries
  def detect(text: String): Locale =
    if (text.exists(JapaneseText.isJapanese))
      Locale.JAPANESE
    else if (text.exists(KoreanText.isKorean))
      Locale.KOREAN
    else
      Locale.ENGLISH
}

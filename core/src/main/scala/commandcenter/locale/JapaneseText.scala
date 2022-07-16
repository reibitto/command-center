package commandcenter.locale

object JapaneseText {

  def isHiragana(c: Char): Boolean =
    c match {
      case code if code >= 0x3041 && code <= 0x3094   => true
      case 0x309b | 0x309c | 0x30fc | 0x30fd | 0x30fe => true
      case _                                          => false
    }

  def isHalfWidthKatakana(c: Char): Boolean = c >= 0xff65 && c <= 0xff9f

  def isFullWidthKatakana(c: Char): Boolean =
    c match {
      case code if code >= 0x30a1 && code <= 0x30fb   => true // Katakana
      case code if code >= 0x31f0 && code <= 0x31ff   => true // Phonetic Extensions for Ainu
      case 0x309b | 0x309c | 0x30fc | 0x30fd | 0x30fe => true
      case _                                          => false
    }

  def isKatakana(c: Char): Boolean = isHalfWidthKatakana(c) || isFullWidthKatakana(c)

  def isKana(c: Char): Boolean =
    (c >= 0x3041 && c <= 0x3094) || // Hiragana (without punctuation/symbols because it's included in the `isKatakana` check)
      isKatakana(c)

  def isKanji(c: Char): Boolean = c >= 0x4e00 && c <= 0x9fcc

  def isJapanese(c: Char): Boolean = isKana(c) || isKanji(c)
}

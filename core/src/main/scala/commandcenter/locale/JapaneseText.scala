package commandcenter.locale

object JapaneseText {
  def isHiragana(c: Char): Boolean = c match {
    case code if code >= 0x3041 && code <= 0x3094   => true
    case 0x309B | 0x309C | 0x30FC | 0x30FD | 0x30FE => true
    case _                                          => false
  }

  def isHalfWidthKatakana(c: Char): Boolean = c >= 0xFF65 && c <= 0xFF9F

  def isFullWidthKatakana(c: Char): Boolean = c match {
    case code if code >= 0x30A1 && code <= 0x30FB   => true // Katakana
    case code if code >= 0x31F0 && code <= 0x31FF   => true // Phonetic Extensions for Ainu
    case 0x309B | 0x309C | 0x30FC | 0x30FD | 0x30FE => true
    case _                                          => false
  }

  def isKatakana(c: Char): Boolean = isHalfWidthKatakana(c) || isFullWidthKatakana(c)

  def isKana(c: Char): Boolean =
    (c >= 0x3041 && c <= 0x3094) || // Hiragana (without punctuation/symbols because it's included in the `isKatakana` check)
      isKatakana(c)

  def isKanji(c: Char): Boolean = c >= 0x4E00 && c <= 0x9FCC

  def isJapanese(c: Char): Boolean = isKana(c) || isKanji(c)
}

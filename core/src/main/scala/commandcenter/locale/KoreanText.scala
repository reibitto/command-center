package commandcenter.locale

object KoreanText {
  def isKorean(c: Char): Boolean =
    c match {
      case code if code >= 0xAC00 && code <= 0xD7A3 => true
      case code if code >= 0x1100 && code <= 0x11FF => true
      case code if code >= 0x3130 && code <= 0x318F => true
      case code if code >= 0xA960 && code <= 0xA97F => true
      case code if code >= 0xD7B0 && code <= 0xD7FF => true
      case _                                        => false
    }
}

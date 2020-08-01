package commandcenter.locale

object KoreanText {
  def isKorean(c: Char): Boolean =
    c match {
      case code if code >= 0xac00 && code <= 0xd7a3 => true
      case code if code >= 0x1100 && code <= 0x11ff => true
      case code if code >= 0x3130 && code <= 0x318f => true
      case code if code >= 0xa960 && code <= 0xa97f => true
      case code if code >= 0xd7b0 && code <= 0xd7ff => true
      case _                                        => false
    }
}

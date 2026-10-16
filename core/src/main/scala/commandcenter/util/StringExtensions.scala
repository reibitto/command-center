package commandcenter.util

object StringExtensions {

  implicit class StringExtension[A](val self: String) extends AnyVal {

    def startsWithIgnoreCase(prefix: String): Boolean =
      self.regionMatches(true, 0, prefix, 0, prefix.length)

    def endsWithIgnoreCase(suffix: String): Boolean = {
      val suffixLength = suffix.length
      self.regionMatches(true, self.length - suffixLength, suffix, 0, suffixLength)
    }

    def containsIgnoreCase(s: String): Boolean = {
      val sLength = s.length
      (0 to self.length - sLength).exists(i => self.regionMatches(true, i, s, 0, sLength))
    }

  }
}

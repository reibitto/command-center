package commandcenter.util

import com.googlecode.lanterna.TerminalTextUtils

object TextUtils {

  def charWidth(c: Char): Int =
    if (TerminalTextUtils.isCharDoubleWidth(c)) 2 else 1
}

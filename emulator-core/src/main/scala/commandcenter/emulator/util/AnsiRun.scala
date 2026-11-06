package commandcenter.emulator.util

final case class AnsiRun(text: String, foreground: Int, background: Int)

object AnsiRun {

  private def colorMask(width: Int): Long = ~0L >>> (64 - width)

  def runs(str: fansi.Str): List[AnsiRun] = {
    val plainText = str.plainText
    val groups = Lists.groupConsecutive(str.getColors.toList)

    groups.zip(groups.scanLeft(0)(_ + _.length)).map { case (group, start) =>
      AnsiRun(
        plainText.substring(start, start + group.length),
        ((group.head >>> fansi.Color.offset) & colorMask(fansi.Color.width)).toInt,
        ((group.head >>> fansi.Back.offset) & colorMask(fansi.Back.width)).toInt
      )
    }
  }
}

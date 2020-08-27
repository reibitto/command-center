package commandcenter.view

sealed trait Rendered

object Rendered {
  final case class Ansi(ansiStr: fansi.Str) extends Rendered

  final case class Styled(segments: Vector[StyledText]) extends Rendered {
    lazy val plainText: String = segments.map(_.text).mkString
  }
}

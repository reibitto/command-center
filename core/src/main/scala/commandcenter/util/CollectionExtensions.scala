package commandcenter.util

object CollectionExtensions {

  implicit class ListExtension[A](val self: List[A]) extends AnyVal {

    def intersperse(separator: A): List[A] =
      (separator, self) match {
        case (_, Nil)             => Nil
        case (_, list @ _ :: Nil) => list
        case (sep, y :: ys)       => y :: sep :: ys.intersperse(sep)
      }
  }
}

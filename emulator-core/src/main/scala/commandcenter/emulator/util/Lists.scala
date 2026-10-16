package commandcenter.emulator.util

import scala.annotation.tailrec

object Lists {

  def groupConsecutive[A](list: List[A]): List[List[A]] = {
    @tailrec
    def loop(rest: List[A], acc: List[List[A]]): List[List[A]] =
      rest match {
        case head :: tail =>
          val (same, next) = tail.span(_ == head)
          loop(next, (head :: same) :: acc)
        case Nil => acc.reverse
      }

    loop(list, Nil)
  }
}

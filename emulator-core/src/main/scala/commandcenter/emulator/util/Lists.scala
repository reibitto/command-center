package commandcenter.emulator.util

import scala.annotation.tailrec

object Lists {
  @tailrec
  def groupConsecutive[A](list: List[A], acc: List[List[A]] = Nil): List[List[A]] =
    list match {
      case head :: tail =>
        val (t1, t2) = tail.span(_ == head)
        groupConsecutive(t2, acc :+ (head :: t1))
      case _            => acc
    }
}

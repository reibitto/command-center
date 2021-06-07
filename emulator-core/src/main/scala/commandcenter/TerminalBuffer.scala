package commandcenter

import scala.collection.mutable

class TerminalBuffer(val buffer: StringBuilder, val lineStartIndices: mutable.ArrayDeque[Int]) {
  def clear(): Unit = {
    buffer.clear()
    lineStartIndices.clear()
  }
}

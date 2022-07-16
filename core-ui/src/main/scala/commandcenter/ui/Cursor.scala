package commandcenter.ui

final case class Cursor(column: Int, row: Int) {
  def +(other: Cursor): Cursor = Cursor(column + other.column, row + other.row)

  def -(other: Cursor): Cursor = Cursor(column - other.column, row - other.row)
}

object Cursor {
  val unit: Cursor = Cursor(0, 0)
}

/**
 * Represents a text cursor position. There is the concept of a "logical"
 * position and an "actual" one. A CJK char can be "wide" meaning it takes up 2
 * width units rather than 1. For example, after typing あ the cursor will
 * advance 1 logical unit and 2 actual units.
 */
final case class TextCursor(logical: Cursor, actual: Cursor) {

  def offsetColumnBy(logicalAmount: Int, actualAmount: Int): TextCursor =
    copy(
      logical.copy(column = logical.column + logicalAmount),
      actual.copy(column = actual.column + actualAmount)
    )
}

object TextCursor {
  val unit: TextCursor = TextCursor(Cursor.unit, Cursor.unit)
}

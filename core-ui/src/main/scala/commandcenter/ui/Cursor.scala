package commandcenter.ui

final case class Cursor(column: Int, row: Int) {
  def +(other: Cursor): Cursor = Cursor(column + other.column, row + other.row)

  def -(other: Cursor): Cursor = Cursor(column - other.column, row - other.row)
}

object Cursor {
  val unit: Cursor = Cursor(0, 0)
}

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

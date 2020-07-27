package commandcenter.view

trait View[A] {
  def render(a: A): Rendered
}

object View {
  def apply[A](f: A => Rendered): View[A] = { a: A => f(a) }

  def ansi[A](f: A => fansi.Str): View[A] = { a: A => AnsiRendered(f(a)) }
}

sealed trait Rendered
final case class AnsiRendered(ansiStr: fansi.Str) extends Rendered

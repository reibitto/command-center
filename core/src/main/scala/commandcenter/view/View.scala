package commandcenter.view

trait View[A] {
  def render(a: A): Rendered = renderStyled(a).getOrElse(renderAnsi(a))

  def renderAnsi(a: A): Rendered.Ansi
  def renderStyled(a: A): Option[Rendered.Styled]
}

object View {
  def apply[A](f: A => Rendered.Ansi): View[A] = new View[A] {
    def renderAnsi(a: A): Rendered.Ansi = f(a)

    def renderStyled(a: A): Option[Rendered.Styled] = None
  }

  def ansi[A](f: A => fansi.Str): View[A] = new View[A] {
    def renderAnsi(a: A): Rendered.Ansi = Rendered.Ansi(f(a))

    def renderStyled(a: A): Option[Rendered.Styled] = None
  }
}

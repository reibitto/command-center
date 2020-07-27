package commandcenter.view

object syntax extends ViewInstances {
  implicit class ViewOps[A](val value: A) extends AnyVal {
    def render(implicit view: View[A]): Rendered = view.render(value)
  }
}

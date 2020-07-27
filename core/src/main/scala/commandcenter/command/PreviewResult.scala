package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.view.syntax._
import commandcenter.view.{ DefaultView, Rendered, View }
import zio.{ RIO, Task }

// TODO: Make source optional?
// TODO: Add optional Render. If None, use the default instance
final case class PreviewResult[+R](
  source: Command[R],
  result: R,
  onRun: RIO[Env, Unit],
  score: Double,
  renderFn: () => Rendered
) {
  def score(score: Double): PreviewResult[R] = copy(score = score)

  def onRun(onRun: RIO[Env, Unit]): PreviewResult[R] = copy(onRun = onRun)

  def renderFn(renderFn: R => Rendered): PreviewResult[R] = copy(renderFn = () => renderFn(result))

  def render[A: View](renderFn: R => A): PreviewResult[R] =
    copy(renderFn = () => implicitly[View[A]].render(renderFn(result)))

  def view[A: View](view: A): PreviewResult[R] = copy(renderFn = () => implicitly[View[A]].render(view))
}

object PreviewResult {
  def of[R: View](source: Command[R], result: R): PreviewResult[R] =
    new PreviewResult[R](source, result, Task.unit, Scores.default, () => DefaultView(source.title, "").render)
}

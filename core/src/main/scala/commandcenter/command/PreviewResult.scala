package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.view.syntax._
import commandcenter.view.{ DefaultView, Rendered, View }
import zio.{ RIO, Task }

sealed trait MoreResults
object MoreResults {
  case object Exhausted                                                 extends MoreResults
  final case class Remaining[A](paginated: PreviewResults.Paginated[A]) extends MoreResults
}

sealed trait RunOption

object RunOption {
  case object Exit       extends RunOption
  case object Hide       extends RunOption
  case object RemainOpen extends RunOption
}

// TODO: Make source optional?
// TODO: Add optional Render. If None, use the default instance
final case class PreviewResult[+A](
  source: Command[A],
  result: A,
  onRun: RIO[Env, Unit],
  runOption: RunOption,
  moreResults: MoreResults,
  score: Double,
  renderFn: () => Rendered
) {
  def runOption(runOption: RunOption): PreviewResult[A] = copy(runOption = runOption)

  def moreResults(moreResults: MoreResults): PreviewResult[A] = copy(moreResults = moreResults)

  def score(score: Double): PreviewResult[A] = copy(score = score)

  def onRun(onRun: RIO[Env, Unit]): PreviewResult[A] = copy(onRun = onRun)

  def rendered(rendered: => Rendered): PreviewResult[A]   = copy(renderFn = () => rendered)
  def renderFn(renderFn: A => Rendered): PreviewResult[A] = copy(renderFn = () => renderFn(result))

  def render[V: View](renderFn: A => V): PreviewResult[A] =
    copy(renderFn = () => implicitly[View[V]].render(renderFn(result)))

  def view[V: View](view: V): PreviewResult[A] = copy(renderFn = () => implicitly[View[V]].render(view))
}

object PreviewResult {
  def of[V: View](source: Command[V], result: V): PreviewResult[V] =
    new PreviewResult[V](
      source,
      result,
      Task.unit,
      RunOption.Hide,
      MoreResults.Exhausted,
      Scores.default,
      () => DefaultView(source.title, "").render
    )

  def unit(source: Command[Unit], rendered: Rendered): PreviewResult[Unit] =
    new PreviewResult(source, (), Task.unit, RunOption.Hide, MoreResults.Exhausted, Scores.default, () => rendered)
}

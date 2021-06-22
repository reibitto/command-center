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
final case class PreviewResult[+R](
  source: Command[R],
  result: R,
  onRun: RIO[Env, Unit],
  runOption: RunOption,
  moreResults: MoreResults,
  score: Double,
  renderFn: () => Rendered
) {
  def runOption(runOption: RunOption): PreviewResult[R] = copy(runOption = runOption)

  def moreResults(moreResults: MoreResults): PreviewResult[R] = copy(moreResults = moreResults)

  def score(score: Double): PreviewResult[R] = copy(score = score)

  def onRun(onRun: RIO[Env, Unit]): PreviewResult[R] = copy(onRun = onRun)

  def rendered(rendered: => Rendered): PreviewResult[R]   = copy(renderFn = () => rendered)
  def renderFn(renderFn: R => Rendered): PreviewResult[R] = copy(renderFn = () => renderFn(result))

  def render[A: View](renderFn: R => A): PreviewResult[R] =
    copy(renderFn = () => implicitly[View[A]].render(renderFn(result)))

  def view[A: View](view: A): PreviewResult[R] = copy(renderFn = () => implicitly[View[A]].render(view))
}

object PreviewResult {
  def of[R: View](source: Command[R], result: R): PreviewResult[R] =
    new PreviewResult[R](
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

package commandcenter.command

import commandcenter.event.KeyboardShortcut
import commandcenter.view.Rendered
import commandcenter.CCRuntime.Env
import zio.{RIO, ZIO}

sealed trait PreviewResult[+A] {
  def onRun: RIO[Env, Unit]
  def runOption: RunOption
  def moreResults: MoreResults
  def score: Double
  def renderFn: () => Rendered
  def shortcuts: Set[KeyboardShortcut]

  def runOption(runOption: RunOption): PreviewResult[A]

  def moreResults(moreResults: MoreResults): PreviewResult[A]

  def score(score: Double): PreviewResult[A]

  def onRun(onRun: RIO[Env, Unit]): PreviewResult[A]

  def rendered(rendered: => Rendered): PreviewResult[A]

  def renderedAnsi(renderedAnsi: => fansi.Str): PreviewResult[A] = rendered(Rendered.Ansi(renderedAnsi))
}

object PreviewResult {

  final case class None(
    onRun: RIO[Env, Unit],
    runOption: RunOption,
    moreResults: MoreResults,
    score: Double,
    renderFn: () => Rendered
  ) extends PreviewResult[Nothing] {
    def runOption(runOption: RunOption): PreviewResult[Nothing] = copy(runOption = runOption)

    def moreResults(moreResults: MoreResults): PreviewResult[Nothing] = copy(moreResults = moreResults)

    def score(score: Double): PreviewResult[Nothing] = copy(score = score)

    def onRun(onRun: RIO[Env, Unit]): PreviewResult[Nothing] = copy(onRun = onRun)

    def rendered(rendered: => Rendered): PreviewResult[Nothing] = copy(renderFn = () => rendered)

    def shortcuts: Set[KeyboardShortcut] = Set.empty
  }

  final case class Some[+A](
    source: Command[A],
    result: A,
    onRun: RIO[Env, Unit],
    runOption: RunOption,
    moreResults: MoreResults,
    score: Double,
    renderFn: () => Rendered
  ) extends PreviewResult[A] {
    def runOption(runOption: RunOption): PreviewResult[A] = copy(runOption = runOption)

    def moreResults(moreResults: MoreResults): PreviewResult[A] = copy(moreResults = moreResults)

    def score(score: Double): PreviewResult[A] = copy(score = score)

    def onRun(onRun: RIO[Env, Unit]): PreviewResult[A] = copy(onRun = onRun)

    def rendered(rendered: => Rendered): PreviewResult[A] = copy(renderFn = () => rendered)
    def renderFn(renderFn: A => Rendered): PreviewResult[A] = copy(renderFn = () => renderFn(result))

    def shortcuts: Set[KeyboardShortcut] = source.shortcuts
  }

  def nothing(rendered: Rendered): PreviewResult[Nothing] =
    PreviewResult.None(ZIO.unit, RunOption.Hide, MoreResults.Exhausted, Scores.default, () => rendered)

  def unit(source: Command[Unit], rendered: Rendered): PreviewResult[Unit] =
    PreviewResult.Some(source, (), ZIO.unit, RunOption.Hide, MoreResults.Exhausted, Scores.default, () => rendered)
}

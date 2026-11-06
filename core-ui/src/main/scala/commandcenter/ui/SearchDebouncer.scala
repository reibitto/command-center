package commandcenter.ui

import commandcenter.util.Debouncer
import commandcenter.CCRuntime.Env
import commandcenter.Conf
import zio.*

object SearchDebouncer {

  def make: URIO[Conf, Debouncer[Env, Nothing, Unit]] =
    Conf.get(_.general).flatMap(c => Debouncer.make[Env, Nothing, Unit](c.debounceDelay, Some(c.opTimeoutOrDefault)))
}

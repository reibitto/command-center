package commandcenter

import commandcenter.CCRuntime.Env
import zio.*

trait Conf {
  def config: UIO[CCConfig]
  def load: RIO[Scope & Env, CCConfig]
  def reload: RIO[Env, CCConfig]
}

object Conf {
  def get[A](f: CCConfig => A): URIO[Conf, A] = config.map(f)

  def config: URIO[Conf, CCConfig] = ZIO.serviceWithZIO[Conf](_.config)

  def load: RIO[Scope & Env, CCConfig] = ZIO.serviceWithZIO[Conf](_.load)

  def reload: RIO[Env, CCConfig] = ZIO.serviceWithZIO[Conf](_.reload)
}

final case class ConfigLive(configRef: Ref[Option[ReloadableConfig]]) extends Conf {

  def config: UIO[CCConfig] = configRef.get.some
    .map(_.config)
    .orDieWith(_ => new Exception("A config hasn't been loaded. You likely forgot to call `Conf.load` on startup."))

  def load: RIO[Scope & Env, CCConfig] =
    (for {
      (release, config) <- Scope.global.use(CCConfig.load.withEarlyRelease)
      _                 <- configRef.set(Some(ReloadableConfig(config, release)))
    } yield config).withFinalizer { _ =>
      ZIO.whenCaseZIO(configRef.get) { case Some(reloadableConfig) =>
        reloadableConfig.release
      }
    }

  // Loads before swapping so a broken config keeps the current one; `getAndSet` releases each config exactly once.
  def reload: RIO[Env, CCConfig] =
    for {
      (release, config) <- Scope.global.use(CCConfig.load.withEarlyRelease)
      previous          <- configRef.getAndSet(Some(ReloadableConfig(config, release)))
      _                 <- ZIO.foreachDiscard(previous)(_.release)
    } yield config
}

object ConfigLive {

  def layer: ZLayer[Any, Nothing, ConfigLive] =
    ZLayer(Ref.make(Option.empty[ReloadableConfig]).map(ConfigLive(_)))

}

final case class ReloadableConfig(config: CCConfig, release: UIO[Unit])

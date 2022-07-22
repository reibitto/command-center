package commandcenter

import commandcenter.CCRuntime.Env
import zio.*

trait Conf {
  def config: UIO[CCConfig]
  def reload: RIO[Any, CCConfig]
}

object Conf {
  def get[A](f: CCConfig => A): URIO[Conf, A] = config.map(f)

  def config: URIO[Conf, CCConfig] = ZIO.serviceWithZIO[Conf](_.config)

  def reload: RIO[Env, CCConfig] =
    for {
      conf   <- ZIO.service[Conf]
      config <- conf.reload
    } yield config
}

final case class ConfigLive(configRef: Ref[CCConfig]) extends Conf {
  def config: UIO[CCConfig] = configRef.get

  def reload: Task[CCConfig] =
    for {
      config <- ZIO.scoped {
                  for {
                    config <- CCConfig.load
                    _      <- configRef.set(config)
                  } yield config
                }
    } yield config
}

object ConfigLive {

  def layer: ZLayer[Scope, Throwable, ConfigLive] = {
    ZLayer.fromZIO(
      for {
        config    <- CCConfig.load
        configRef <- Ref.make(config)
      } yield ConfigLive(configRef)
    )

  }
}

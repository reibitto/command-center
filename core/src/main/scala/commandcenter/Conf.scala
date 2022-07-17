package commandcenter

import commandcenter.CCRuntime.{Env, PartialEnv}
import zio.*
import zio.managed.Reservation

trait Conf {
  def config: UIO[CCConfig]
  def reload: RIO[PartialEnv, CCConfig]
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

  def reload: RIO[PartialEnv, CCConfig] =
    ???
//    (for {
//      _                   <- CCConfig.validateConfig
//      previousReservation <- reservationRef.get
//      _                   <- previousReservation.release(Exit.unit)
//      reservation         <- CCConfig.load.fromReservation
//      config <- reservation.acquire.tapError { t =>
//                  ZIO.logWarningCause(
//                    "Command Center did not load properly with the new config and might now be in an invalid state.",
//                    Cause.fail(t)
//                  )
//                }
//      _ <- configRef.set(config)
//      _ <- reservationRef.set(reservation)
//    } yield config).tapError { t =>
//      ZIO.logWarningCause("Could not reload config because the config file is not valid", Cause.fail(t))
//    }
}

object ConfigLive {

  def layer = {
    ZLayer.fromZIO(
      for {
        config    <- CCConfig.load
        configRef <- Ref.make(config)
      } yield ConfigLive(configRef)
    )

  }
  //    (for {
//      reservation    <- CCConfig.load.fromReservation.toManaged
//      config         <- reservation.acquire.toManaged
//      ref            <- Ref.makeManaged(config)
//      reservationRef <- Ref.makeManaged(reservation)
//      _              <- ZIO.unit.toManagedWith(_ => reservation.release(Exit.unit))
//    } yield ConfigLive(ref, reservationRef)).toLayer
}

package commandcenter

import commandcenter.CCRuntime.{ Env, PartialEnv }
import zio._
import zio.logging.log

trait Conf {
  def config: UIO[CCConfig]
  def reload: RIO[PartialEnv, CCConfig]
}

object Conf      {
  def get[A](f: CCConfig => A): URIO[Has[Conf], A] = config.map(f)

  def config: URIO[Has[Conf], CCConfig] = ZIO.serviceWith[Conf](_.config)

  def reload: RIO[Env, CCConfig] =
    for {
      conf   <- ZIO.service[Conf]
      config <- conf.reload
    } yield config
}

final case class ConfigLive(configRef: Ref[CCConfig], reservationRef: Ref[Reservation[PartialEnv, Throwable, CCConfig]])
    extends Conf {
  def config: UIO[CCConfig] = configRef.get

  def reload: RIO[PartialEnv, CCConfig] =
    (for {
      _                   <- CCConfig.validateConfig
      previousReservation <- reservationRef.get
      _                   <- previousReservation.release(Exit.unit)
      reservation         <- CCConfig.load.reserve
      config              <- reservation.acquire.tapError { t =>
                               log.throwable(
                                 "Command Center did not load properly with the new config and might now be in an invalid state.",
                                 t
                               )
                             }
      _                   <- configRef.set(config)
      _                   <- reservationRef.set(reservation)
    } yield config).tapError { t =>
      log.throwable("Could not reload config because the config file is not valid", t)
    }
}

object ConfigLive {
  def layer: RLayer[PartialEnv, Has[Conf]] =
    (for {
      reservation    <- CCConfig.load.reserve.toManaged_
      config         <- reservation.acquire.toManaged_
      ref            <- Ref.makeManaged(config)
      reservationRef <- Ref.makeManaged(reservation)
      _              <- ZIO.unit.toManaged(_ => reservation.release(Exit.unit))
    } yield ConfigLive(ref, reservationRef)).toLayer
}

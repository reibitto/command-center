package commandcenter.cache

import org.cache2k.config.Cache2kConfig
import org.cache2k.io.AsyncCacheLoader
import org.cache2k.Cache
import org.cache2k.Cache2kBuilder
import zio.*

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

/** Wraps the Cache2k object to provide safe methods and better integrate with
  * ZIO.
  */
class ZCache[K, V](underlying: Cache[K, V]) {

  def apply(key: K): Task[V] =
    ZIO
      .attempt(Option(underlying.get(key)))
      .someOrFail(
        new Exception(s"Key `$key` not found in cache")
      )

  // When using a loading cache, a `CacheLoaderException` will be thrown if the load function failed, hence why this
  // returns a `Task`. You may or may not want to handle this exception explicitly in the error channel.
  def get(key: K): Task[Option[V]] =
    ZIO.attempt(Option(underlying.get(key)))

  def getOrElseUpdate(key: K, expiration: Duration)(orElse: => Task[V]): Task[V] =
    ZIO.succeed {
      var time = 0L

      val value = underlying.computeIfAbsent(
        key,
        k =>
          Unsafe.unsafe { implicit u =>
            Runtime.default.unsafe
              .run(
                for {
                  v           <- orElse
                  currentTime <- Clock.currentTime(TimeUnit.MILLISECONDS)
                } yield {
                  time = currentTime
                  v
                }
              )
              .getOrThrowFiberFailure()
          }
      )

      underlying.expireAt(key, time + expiration.toMillis)

      value
    }

  def contains(key: K): UIO[Boolean] =
    ZIO.succeed(underlying.containsKey(key))

  def entries: UIO[Map[K, Option[V]]] = ZIO.succeed(
    underlying.entries.asScala.map { x =>
      x.getKey -> Option(x.getValue)
    }.toMap
  )

  def set(key: K, value: V, expiration: Duration): UIO[Unit] =
    for {
      currentTime <- Clock.currentTime(TimeUnit.MILLISECONDS)
      _ = underlying.invoke(key, _.setValue(value).setExpiryTime(currentTime + expiration.toMillis))
    } yield ()

  def invalidate(key: K): UIO[Unit] =
    ZIO.succeed(underlying.remove(key))

  def invalidateAll(keys: K*): UIO[Unit] =
    ZIO.succeed(underlying.removeAll(keys.asJava))

  def clear: UIO[Unit] =
    ZIO.succeed(underlying.clear())
}

object ZCache {

  def make[K, V](capacity: Long = Cache2kConfig.DEFAULT_ENTRY_CAPACITY): ZCache[K, V] = {
    val cache = Cache2kBuilder
      .of(new Cache2kConfig[K, V]())
      .entryCapacity(capacity)
      .permitNullValues(true)
      .build()

    new ZCache(cache)
  }

  def memoize[K, V](
      capacity: Long = Cache2kConfig.DEFAULT_ENTRY_CAPACITY,
      expireAfter: Option[Duration] = None
  )(op: K => V): ZCache[K, V] = {
    val builder = Cache2kBuilder
      .of(new Cache2kConfig[K, V]())
      .entryCapacity(capacity)
      .permitNullValues(true)
      .loader((input: K) => op(input))

    val cache = expireAfter.fold(builder.build)(expireAfter => builder.expireAfterWrite(expireAfter).build)

    new ZCache(cache)
  }

  def memoizeZIO[K, V, R, E](
      capacity: Long = Cache2kConfig.DEFAULT_ENTRY_CAPACITY,
      expireAfter: Option[Duration] = None
  )(op: K => ZIO[R, E, Option[V]])(implicit runtime: Runtime[R]): ZCache[K, V] = {

    val builder = Cache2kBuilder
      .of(new Cache2kConfig[K, V]())
      .entryCapacity(capacity)
      .permitNullValues(true)
      .loader(new AsyncCacheLoader[K, V] {

        def load(
            key: K,
            context: AsyncCacheLoader.Context[K, V],
            callback: AsyncCacheLoader.Callback[V]
        ): Unit =
          Unsafe.unsafe { implicit u =>
            runtime.unsafe.fork(
              op(key).foldCauseZIO(
                e =>
                  ZIO.succeed(
                    callback.onLoadFailure(
                      e.squashWith {
                        case t: Throwable => t
                        case other        => new Exception(s"Cache loading failed due to underlying cause: $other")
                      }
                    )
                  ),
                {
                  case Some(v) => ZIO.succeed(callback.onLoadSuccess(v))
                  case None    => ZIO.succeed(callback.onLoadSuccess(null.asInstanceOf[V]))
                }
              )
            )
          }

      })

    val cache = expireAfter.fold(builder.build)(expireAfter => builder.expireAfterWrite(expireAfter).build)

    new ZCache(cache)
  }
}

package commandcenter.cache

import com.github.benmanes.caffeine.cache.{AsyncCacheLoader, AsyncLoadingCache, Caffeine, Expiry}
import zio.*

import java.util.concurrent
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.FutureOps

/** Wraps the caffeine cache object to provide safe methods and better integrate
  * with ZIO.
  */
class ZCache[K, V](underlying: AsyncLoadingCache[K, V]) {

  def apply(key: K): Task[V] =
    ZIO
      .succeed(Option(underlying.synchronous.get(key)))
      .someOrFail(
        new Exception(s"Key `$key` not found in cache")
      )

  // When using a loading cache, a `CacheLoaderException` will be thrown if the load function failed, hence why this
  // returns a `Task`. You may or may not want to handle this exception explicitly in the error channel.
  def get(key: K): Task[Option[V]] =
    ZIO.succeed(Option(underlying.synchronous.get(key)))

  def getOrElseUpdate(key: K, expiration: Duration)(orElse: => Task[V]): Task[V] = {
    val value = underlying
      .asMap()
      .computeIfAbsent(
        key,
        k =>
          Unsafe.unsafe { implicit u =>
            Runtime.default.unsafe
              .runToFuture(orElse)
              .asJava
              .toCompletableFuture
          }
      )

    underlying.synchronous.policy.expireVariably.ifPresent(_.setExpiresAfter(key, expiration))

    ZIO.fromCompletableFuture(value)
  }

  def contains(key: K): UIO[Boolean] =
    ZIO.succeed(underlying.asMap.containsKey(key))

  def entries: UIO[Map[K, V]] = ZIO.succeed(
    underlying.synchronous.asMap.asScala.toMap
  )

  def set(key: K, value: V, expiration: Duration): UIO[Unit] =
    ZIO.succeed(
      underlying.synchronous.policy.expireVariably.ifPresent { x =>
        x.put(key, value, expiration)
      }
    )

  def invalidate(key: K): UIO[Unit] =
    ZIO.succeed(underlying.synchronous.invalidate(key))

  def invalidateAll(key: K, keys: K*): UIO[Unit] =
    ZIO.succeed(underlying.synchronous.invalidateAll((key +: keys).asJava))

  def clear: UIO[Unit] =
    ZIO.succeed(underlying.synchronous.invalidateAll())
}

object ZCache {

  def make[K, V](capacity: Int = 32): ZCache[K, V] = {
    val cacheLoader: AsyncCacheLoader[K, V] = (key: K, executor: concurrent.Executor) =>
      CompletableFuture.completedFuture(null.asInstanceOf[V])

    val cache = Caffeine
      .newBuilder()
      .initialCapacity(capacity)
      .expireAfter(new Expiry[K, V] {
        def expireAfterCreate(key: K, value: V, currentTime: Long): Long = Long.MaxValue

        def expireAfterUpdate(key: K, value: V, currentTime: Long, currentDuration: Long): Long = currentDuration

        def expireAfterRead(key: K, value: V, currentTime: Long, currentDuration: Long): Long = currentDuration
      })
      .buildAsync[K, V](cacheLoader)

    new ZCache(cache)
  }

  def memoize[K, V](
      capacity: Int = 32,
      expireAfter: Option[Duration] = None
  )(op: K => V): ZCache[K, V] = {

    val builder = Caffeine
      .newBuilder()
      .expireAfter(new Expiry[K, V] {
        def expireAfterCreate(key: K, value: V, currentTime: Long): Long = expireAfter.fold(Long.MaxValue)(_.toNanos)

        def expireAfterUpdate(key: K, value: V, currentTime: Long, currentDuration: Long): Long = currentDuration

        def expireAfterRead(key: K, value: V, currentTime: Long, currentDuration: Long): Long = currentDuration
      })
      .initialCapacity(capacity)

    val cacheLoader: AsyncCacheLoader[K, V] = (key: K, executor: concurrent.Executor) =>
      CompletableFuture.completedFuture(op(key))

    new ZCache(builder.buildAsync[K, V](cacheLoader))
  }

  def memoizeZIO[K, V, R, E](
      capacity: Int = 32,
      expireAfter: Option[Duration] = None
  )(op: K => ZIO[R, E, Option[V]])(implicit runtime: Runtime[R]): ZCache[K, V] = {

    val builder = Caffeine
      .newBuilder()
      .expireAfter(new Expiry[K, V] {
        def expireAfterCreate(key: K, value: V, currentTime: Long): Long = expireAfter.fold(Long.MaxValue)(_.toNanos)

        def expireAfterUpdate(key: K, value: V, currentTime: Long, currentDuration: Long): Long = currentDuration

        def expireAfterRead(key: K, value: V, currentTime: Long, currentDuration: Long): Long = currentDuration
      })
      .initialCapacity(capacity)

    val cacheLoader: AsyncCacheLoader[K, V] = (key: K, executor: concurrent.Executor) =>
      Unsafe.unsafe { implicit u =>
        runtime.unsafe.runToFuture(
          op(key).foldCauseZIO(
            e =>
              ZIO.fail(
                e.squashWith {
                  case t: Throwable => t
                  case other        => new Exception(s"Cache loading failed due to underlying cause: $other")
                }
              ),
            {
              case Some(a) => ZIO.succeed(a)
              case None    => ZIO.succeed(null.asInstanceOf[V])
            }
          )
        )
      }.asJava.toCompletableFuture

    new ZCache(builder.buildAsync[K, V](cacheLoader))
  }
}

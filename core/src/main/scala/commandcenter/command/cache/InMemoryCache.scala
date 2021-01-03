package commandcenter.command.cache

import java.time.Duration
import java.util.concurrent.TimeUnit

import org.cache2k.{ Cache, Cache2kBuilder }
import zio._
import zio.clock.Clock

object InMemoryCache {
  trait Service[K, V] {
    def get[V2](key: K): Task[Option[V2]]
    def getOrElseUpdate[V2 <: V](key: K, ttl: Duration)(orElse: => Task[V2]): RIO[Clock, V2]
    def set(key: K, value: V): Task[Unit]
    def set(key: K, value: V, ttl: Duration): RIO[Clock, Unit]
    def remove(key: K): Task[Unit]
    def removeAll(keys: K*): Task[Unit]
    def clear: Task[Unit]
  }

  // TODO: Default cache is Cache[String, Any]. May want to support custom type params in the future.
  def make(defaultTtl: Duration): TaskLayer[InMemoryCache] =
    ZLayer.succeed(
      new InMemoryCacheImpl(
        Cache2kBuilder
          .forUnknownTypes()
          .expireAfterWrite(defaultTtl.toMillis, TimeUnit.MILLISECONDS)
          .build()
          .asInstanceOf[Cache[String, Any]]
      )
    )

  def get[V](key: String): RIO[InMemoryCache, Option[V]] = ZIO.accessM[InMemoryCache](_.get.get[V](key))

  def getOrElseUpdate[V](key: String, ttl: Duration)(orElse: => Task[V]): RIO[InMemoryCache with Clock, V] =
    ZIO.accessM[InMemoryCache with Clock](_.get.getOrElseUpdate[V](key, ttl)(orElse))

  def set(key: String, value: Any): RIO[InMemoryCache, Unit] =
    ZIO.accessM[InMemoryCache](_.get.set(key, value))

  def set(key: String, value: Any, ttl: Duration): RIO[InMemoryCache with Clock, Unit] =
    ZIO.accessM[InMemoryCache with Clock](_.get.set(key, value, ttl))

  def remove(key: String): RIO[InMemoryCache, Unit] = ZIO.accessM[InMemoryCache](_.get.remove(key))

  def removeAll(keys: String*): RIO[InMemoryCache, Unit] = ZIO.accessM[InMemoryCache](_.get.removeAll(keys: _*))

  def clear: RIO[InMemoryCache, Unit] = ZIO.accessM[InMemoryCache](_.get.clear)
}

class InMemoryCacheImpl[K, V](cache: Cache[K, V]) extends InMemoryCache.Service[K, V] {
  import scala.jdk.CollectionConverters._

  def get[V2](key: K): Task[Option[V2]] =
    for {
      rawValue <- Task(cache.get(key))
      result   <- rawValue match {
                    case null => UIO.none
                    // This can throw an exception, but we're okay with it here since the backing cache has unknown types for the values.
                    case some => Task(some.asInstanceOf[V2]).asSome
                  }
    } yield result

  def getOrElseUpdate[V2 <: V](key: K, ttl: Duration)(orElse: => Task[V2]): RIO[Clock, V2] =
    // TODO: Ideally this should be atomic
    get[V2](key).someOrElseM {
      for {
        rawValue <- orElse
        _        <- set(key, rawValue, ttl)
      } yield rawValue
    }

  def set(key: K, value: V): Task[Unit] = Task(cache.put(key, value))

  def set(key: K, value: V, ttl: Duration): RIO[Clock, Unit] =
    for {
      expiryTime <- clock.currentTime(TimeUnit.MILLISECONDS).map(_ + ttl.toMillis)
      _          <- Task(cache.invoke(key, _.setValue(value).setExpiryTime(expiryTime)))
    } yield ()

  def remove(key: K): Task[Unit] = Task(cache.remove(key))

  def removeAll(keys: K*): Task[Unit] = Task(cache.removeAll(keys.asJava))

  def clear: Task[Unit] = Task(cache.clear())
}

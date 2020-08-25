package commandcenter.command

import zio.Has

package object cache {
  type InMemoryCache = Has[InMemoryCache.Service[String, Any]]
}

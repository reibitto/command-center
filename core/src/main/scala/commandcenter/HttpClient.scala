package commandcenter

import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import zio.Task

object HttpClient {

  val backend: SttpBackend[Task, ZioStreams] = ???

}

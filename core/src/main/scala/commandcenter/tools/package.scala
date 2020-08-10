package commandcenter

import java.io.File

import zio._
import zio.blocking.Blocking

import scala.util.Try

package object tools {

  type Tools = Has[Tools.Service]

  object Tools {
    trait Service {
      def processId: UIO[Long]
      def activate: RIO[Blocking, Unit]
      def hide: RIO[Blocking, Unit]
      def setClipboard(text: String): RIO[Blocking, Unit]
    }

    def live: TaskLayer[Tools] =
      ZLayer.fromManaged(
        for {
          pid      <- Task(ProcessHandle.current.pid).toManaged_
          toolsPath = sys.env.get("COMMAND_CENTER_TOOLS_PATH").map(new File(_)).orElse {
                        Try(System.getProperty("user.home")).toOption
                          .map(home => new File(home, ".command-center/cc-tools"))
                          .filter(_.exists())
                      }
        } yield new LiveTools(pid, toolsPath)
      )
  }

  def processId: URIO[Tools, Long] = ZIO.accessM[Tools](_.get.processId)

  def activate: ZIO[Tools with Blocking, Throwable, Unit] = ZIO.accessM[Tools with Blocking](_.get.activate)

  def hide: ZIO[Tools with Blocking, Throwable, Unit] = ZIO.accessM[Tools with Blocking](_.get.hide)

  def setClipboard(text: String): ZIO[Tools with Blocking, Throwable, Unit] =
    ZIO.accessM[Tools with Blocking](_.get.setClipboard(text))
}

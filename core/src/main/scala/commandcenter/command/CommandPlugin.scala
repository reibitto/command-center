package commandcenter.command

import com.typesafe.config.Config
import commandcenter.util.OS
import io.circe
import io.circe.Decoder
import io.circe.config.syntax._
import zio.{ Task, ZIO }

trait CommandPlugin[A <: Command[_]] {
  val decoder: Decoder[A]

  def load(config: Config): Either[circe.Error, A] =
    config.as[A](decoder)

}

object CommandPlugin {
  def load(config: Config, path: String): Task[List[Command[Any]]] = {
    import scala.jdk.CollectionConverters._

    for {
      commandConfigs <- Task(config.getConfigList(path).asScala.toList)
      commands       <- Task.foreach(commandConfigs)(c => ZIO.fromEither(Command.parse(c)))
    } yield commands.filter(c => c.supportedOS.isEmpty || c.supportedOS.contains(OS.os))
  }

  def loadDynamically(config: Config, path: String): Task[List[Command[Any]]] = {
    import scala.jdk.CollectionConverters._

    val mirror = scala.reflect.runtime.universe.runtimeMirror(CommandPlugin.getClass.getClassLoader)

    for {
      commandConfigs <- Task(config.getConfigList(path).asScala.toList)
      commands       <- Task.foreach(commandConfigs) { c =>
                          for {
                            typeName    <- Task(c.getString("type"))
                            pluginClass <- Task(Class.forName(typeName))
                            plugin      <- Task(
                                             mirror
                                               .reflectModule(mirror.moduleSymbol(pluginClass))
                                               .instance
                                               .asInstanceOf[CommandPlugin[Command[_]]]
                                           )
                            command     <- Task.fromEither(plugin.load(c))
                          } yield command
                        }
    } yield commands.filter(c => c.supportedOS.isEmpty || c.supportedOS.contains(OS.os))
  }
}

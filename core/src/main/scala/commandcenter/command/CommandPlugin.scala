package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.config.ConfigParserExtensions
import commandcenter.util.OS
import zio.{ RManaged, Task, ZManaged }

trait CommandPlugin[A <: Command[_]] extends ConfigParserExtensions {
  def make(config: Config): RManaged[Env, A]
}

object CommandPlugin {
  def load(config: Config, path: String): RManaged[Env, List[Command[Any]]] = {
    import scala.jdk.CollectionConverters._

    for {
      commandConfigs <- Task(config.getConfigList(path).asScala.toList).toManaged_
      commands       <- ZManaged.foreach(commandConfigs)(c => Command.parse(c))
    } yield commands.filter(c => c.supportedOS.isEmpty || c.supportedOS.contains(OS.os))
  }

  def loadDynamically(config: Config, path: String): RManaged[Env, List[Command[Any]]] = {
    import scala.jdk.CollectionConverters._

    val mirror = scala.reflect.runtime.universe.runtimeMirror(CommandPlugin.getClass.getClassLoader)

    for {
      commandConfigs <- Task(config.getConfigList(path).asScala.toList).toManaged_
      commands       <- ZManaged.foreach(commandConfigs) { c =>
                          for {
                            typeName    <- Task(c.getString("type")).toManaged_
                            pluginClass <- Task(Class.forName(typeName)).toManaged_
                            plugin      <- Task(
                                             mirror
                                               .reflectModule(mirror.moduleSymbol(pluginClass))
                                               .instance
                                               .asInstanceOf[CommandPlugin[Command[_]]]
                                           ).toManaged_
                            command     <- plugin.make(c)
                          } yield command
                        }
    } yield commands.filter(c => c.supportedOS.isEmpty || c.supportedOS.contains(OS.os))
  }
}

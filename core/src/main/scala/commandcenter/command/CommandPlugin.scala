package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.config.ConfigParserExtensions
import commandcenter.util.OS
import zio.logging.log
import zio.{ RManaged, Task, ZManaged }

trait CommandPlugin[A <: Command[_]] extends ConfigParserExtensions {
  def make(config: Config): RManaged[Env, A]
}

object CommandPlugin {
  def loadAll(config: Config, path: String): RManaged[Env, List[Command[Any]]] = {
    import scala.jdk.CollectionConverters._

    for {
      commandConfigs <- Task(config.getConfigList(path).asScala.toList).toManaged_
      commands       <- ZManaged.foreach(commandConfigs) { c =>
                          Command
                            .parse(c)
                            .foldM(
                              {
                                case CommandPluginError.PluginNotFound(typeName, _) =>
                                  log.warn(s"Plugin '$typeName' not found").toManaged_ *>
                                    ZManaged.succeed(None)

                                case CommandPluginError.PluginsNotSupported(typeName) =>
                                  log
                                    .warn(
                                      s"Cannot load `$typeName` because external plugins not yet supported for Substrate VM."
                                    )
                                    .toManaged_ *>
                                    ZManaged.succeed(None)

                                case other =>
                                  ZManaged.fail(other)
                              },
                              c => ZManaged.succeed(Some(c))
                            )
                        }
    } yield commands.flatten.filter(c => c.supportedOS.isEmpty || c.supportedOS.contains(OS.os))
  }

  def loadDynamically(c: Config, typeName: String): ZManaged[Env, CommandPluginError, Command[Any]] = {
    val mirror = scala.reflect.runtime.universe.runtimeMirror(CommandPlugin.getClass.getClassLoader)

    for {
      pluginClass <- Task(Class.forName(typeName)).mapError {
                       case e: ClassNotFoundException => CommandPluginError.PluginNotFound(typeName, e)
                       case other                     => CommandPluginError.UnexpectedException(other)
                     }.toManaged_
      plugin      <- Task(
                       mirror
                         .reflectModule(mirror.moduleSymbol(pluginClass))
                         .instance
                         .asInstanceOf[CommandPlugin[Command[_]]]
                     ).toManaged_.mapError(CommandPluginError.UnexpectedException)
      command     <- plugin.make(c).mapError(CommandPluginError.UnexpectedException)
    } yield command
  }
}

sealed abstract class CommandPluginError(cause: Throwable) extends Exception(cause) with Product with Serializable

object CommandPluginError {
  final case class PluginNotFound(typeName: String, cause: Throwable) extends CommandPluginError(cause)
  final case class PluginsNotSupported(typeName: String)              extends CommandPluginError(null)
  final case class UnexpectedException(cause: Throwable)              extends CommandPluginError(cause)
}

package commandcenter.command

import com.typesafe.config.Config
import commandcenter.config.ConfigParserExtensions
import commandcenter.util.OS
import commandcenter.CCRuntime.PartialEnv
import zio.managed.*
import zio.{Scope, ZIO}

trait CommandPlugin[A <: Command[?]] extends ConfigParserExtensions {
  def make(config: Config): RManaged[PartialEnv, A]
}

object CommandPlugin {

  def loadAll(config: Config, path: String) = {
    import scala.jdk.CollectionConverters.*

    for {
      commandConfigs <-
        ZIO
          .attempt(config.getConfigList(path).asScala.toList)
          .mapError(CommandPluginError.UnexpectedException)
      commands <- ZIO.foreach(commandConfigs) { c =>
                    Command
                      .parse(c)
                      .foldZIO(
                        {
                          case CommandPluginError.PluginNotApplicable(commandType, reason) =>
                            ZIO
                              .logDebug(
                                s"Skipping loading `$commandType` plugin because it's not applicable: $reason"
                              ) *>
                              ZIO.succeed(None)

                          case CommandPluginError.PluginNotFound(typeName, _) =>
                            ZIO.logWarning(s"Plugin '$typeName' not found") *>
                              ZIO.succeed(None)

                          case CommandPluginError.PluginsNotSupported(typeName) =>
                            ZIO
                              .logWarning(
                                s"Cannot load `$typeName` because external plugins not yet supported for Substrate VM."
                              ) *>
                              ZIO.succeed(None)

                          case error: CommandPluginError.UnexpectedException =>
                            ZIO.fail(error)

                        },
                        c => ZIO.succeed(Some(c))
                      )
                  }
    } yield commands.flatten.filter(c => c.supportedOS.isEmpty || c.supportedOS.contains(OS.os))
  }

  def loadDynamically(c: Config, typeName: String): ZIO[Scope & PartialEnv, CommandPluginError, Command[Any]] = {
    ???
//    val mirror = scala.reflect.runtime.universe.runtimeMirror(CommandPlugin.getClass.getClassLoader)
//
//    for {
//      pluginClass <- ZIO
//                       .attempt(Class.forName(typeName))
//                       .mapError {
//                         case e: ClassNotFoundException => CommandPluginError.PluginNotFound(typeName, e)
//                         case other                     => CommandPluginError.UnexpectedException(other)
//                       }
//      plugin <- ZIO
//                  .attempt(
//                    mirror
//                      .reflectModule(mirror.moduleSymbol(pluginClass))
//                      .instance
//                      .asInstanceOf[CommandPlugin[Command[?]]]
//                  )
//                  .mapError(CommandPluginError.UnexpectedException)
//      command <- plugin.make(c).mapError(CommandPluginError.UnexpectedException)
//    } yield command
  }
}

sealed abstract class CommandPluginError(cause: Throwable) extends Exception(cause) with Product with Serializable

object CommandPluginError {
  final case class PluginNotApplicable(commandType: CommandType, reason: String) extends CommandPluginError(null)
  final case class PluginNotFound(typeName: String, cause: Throwable) extends CommandPluginError(cause)
  final case class PluginsNotSupported(typeName: String) extends CommandPluginError(null)
  final case class UnexpectedException(cause: Throwable) extends CommandPluginError(cause)
}

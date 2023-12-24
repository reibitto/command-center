package commandcenter

import com.typesafe.config.{Config as TypesafeConfig, ConfigFactory}
import commandcenter.command.{Command, CommandPlugin}
import commandcenter.config.ConfigParserExtensions.*
import commandcenter.config.Decoders.*
import commandcenter.event.KeyboardShortcut
import commandcenter.util.OS
import commandcenter.CCRuntime.Env
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.LowerCamelcase
import io.circe.Decoder
import zio.*

import java.awt.Font
import java.io.File
import scala.concurrent.duration.Duration as ScalaDuration

final case class CCConfig(
    commands: Vector[Command[Any]],
    aliases: Map[String, List[String]],
    general: GeneralConfig,
    display: DisplayConfig,
    keyboard: KeyboardConfig,
    globalActions: Vector[GlobalAction]
)

object CCConfig {

  def defaultConfigFile: UIO[File] =
    envConfigFile.orElse(homeConfigFile).catchAll(_ => ZIO.succeed(new File("application.conf")))

  def load: ZIO[Scope & Env, Throwable, CCConfig] =
    for {
      file   <- defaultConfigFile
      _      <- ZIO.logDebug(s"Loading config file at ${file.getAbsolutePath}")
      config <- load(file)
    } yield config

  def load(file: File): ZIO[Scope & Env, Throwable, CCConfig] =
    for {
      config   <- ZIO.attemptBlocking(ConfigFactory.parseFile(file))
      ccConfig <- loadFrom(config)
    } yield ccConfig

  def loadFrom(config: TypesafeConfig): ZIO[Scope & Env, Exception, CCConfig] =
    for {
      commands       <- CommandPlugin.loadAll(config, "commands")
      aliases        <- ZIO.fromEither(config.as[Map[String, List[String]]]("aliases"))
      generalConfig  <- ZIO.fromEither(config.as[GeneralConfig]("general"))
      displayConfig  <- ZIO.fromEither(config.as[DisplayConfig]("display"))
      keyboardConfig <- ZIO.fromEither(config.as[KeyboardConfig]("keyboard"))
      globalActions  <- ZIO.fromEither(config.get[Vector[GlobalAction]]("globalActions"))
    } yield CCConfig(
      commands.toVector,
      aliases,
      generalConfig,
      displayConfig,
      keyboardConfig,
      globalActions.filter(_.shortcut.nonEmpty)
    )

  private def envConfigFile: ZIO[Any, Option[SecurityException], File] =
    for {
      configPathOpt <- zio.System.env("COMMAND_CENTER_CONFIG_PATH").asSomeError
      configPath    <- ZIO.fromOption(configPathOpt)
      file = new File(configPath)
    } yield if (file.isDirectory) new File(file, "application.conf") else file

  private def homeConfigFile: ZIO[Any, Option[Throwable], File] =
    for {
      userHome <- zio.System.propertyOrElse("user.home", "").asSomeError
      potentialFile = new File(userHome, "/.command-center/application.conf")
      file <- ZIO.fromOption(Option.when(potentialFile.exists())(potentialFile))
    } yield file
}

final case class GeneralConfig(
    debounceDelay: Duration,
    /** If defined the window will be opened again after the specified delay has
      * elapsed. This is used as a hack to get around apps/games that do weird
      * things like steal focus or force "always on top".
      */
    reopenDelay: Option[Duration],
    hideOnKeyRelease: Boolean,
    keepOpen: Boolean
)

object GeneralConfig {

  implicit val decoder: Decoder[GeneralConfig] =
    Decoder.instance { c =>
      for {
        debounceDelay    <- c.get[ScalaDuration]("debounceDelay")
        reopenDelay      <- c.get[Option[ScalaDuration]]("reopenDelay")
        hideOnKeyRelease <- c.get[Option[Boolean]]("hideOnKeyRelease")
        keepOpen         <- c.get[Option[Boolean]]("keepOpen")
      } yield GeneralConfig(
        Duration.fromScala(debounceDelay),
        reopenDelay.map(Duration.fromScala),
        hideOnKeyRelease.getOrElse(false),
        keepOpen.getOrElse(false)
      )
    }
}

final case class DisplayConfig(width: Int, maxHeight: Int, opacity: Float, alternateOpacity: Option[Float], fonts: List[Font])

object DisplayConfig {

  implicit val decoder: Decoder[DisplayConfig] =
    Decoder.forProduct5("width", "maxHeight", "opacity", "alternateOpacity", "fonts")(DisplayConfig.apply)
}

final case class KeyboardConfig(openShortcut: KeyboardShortcut, suspendShortcut: Option[String])

object KeyboardConfig {

  implicit val decoder: Decoder[KeyboardConfig] = Decoder.instance { c =>
    for {
      openShortcut <-
        c.get[KeyboardShortcut](s"openShortcut_${OS.os.entryName}").orElse(c.get[KeyboardShortcut]("openShortcut"))
      suspendShortcut <- c.get[Option[String]]("suspendShortcut")
    } yield KeyboardConfig(openShortcut, suspendShortcut)
  }
}

// TODO: Make this an ADT with special configs for each case
final case class GlobalAction(id: GlobalActionId, shortcut: KeyboardShortcut)

object GlobalAction {

  implicit val decoder: Decoder[GlobalAction] = Decoder.instance { c =>
    for {
      id       <- c.get[GlobalActionId]("id")
      shortcut <- c.get[KeyboardShortcut]("shortcut")
    } yield GlobalAction(id, shortcut)
  }
}

sealed trait GlobalActionId extends EnumEntry with LowerCamelcase

object GlobalActionId extends Enum[GlobalActionId] with CirceEnum[GlobalActionId] {
  case object MinimizeWindow extends GlobalActionId
  case object MaximizeWindow extends GlobalActionId
  case object ToggleMaximizeWindow extends GlobalActionId
  case object CenterWindow extends GlobalActionId
  case object MoveToNextScreen extends GlobalActionId
  case object MoveToPreviousScreen extends GlobalActionId
  case object ResizeToScreenSize extends GlobalActionId
  case object ResizeFullHeightMaintainAspectRatio extends GlobalActionId
  case object CycleWindowSizeLeft extends GlobalActionId
  case object CycleWindowSizeRight extends GlobalActionId
  case object CycleWindowSizeTop extends GlobalActionId
  case object CycleWindowSizeBottom extends GlobalActionId

  lazy val values: IndexedSeq[GlobalActionId] = findValues
}

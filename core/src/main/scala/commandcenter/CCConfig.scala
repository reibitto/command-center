package commandcenter

import com.typesafe.config.{ Config => TypesafeConfig, ConfigFactory }
import commandcenter.CCRuntime.PartialEnv
import commandcenter.command.{ Command, CommandPlugin }
import commandcenter.config.ConfigParserExtensions._
import commandcenter.config.Decoders._
import commandcenter.event.KeyboardShortcut
import commandcenter.util.OS
import enumeratum.EnumEntry.LowerCamelcase
import enumeratum.{ CirceEnum, Enum, EnumEntry }
import io.circe.Decoder
import zio.blocking._
import zio.duration._
import zio.logging.{ log, Logging }
import zio.system.System
import zio.{ RManaged, UIO, ZIO, ZManaged }

import java.awt.Font
import java.io.File

final case class CCConfig(
  commands: Vector[Command[Any]],
  aliases: Map[String, List[String]],
  general: GeneralConfig,
  display: DisplayConfig,
  keyboard: KeyboardConfig,
  globalActions: Vector[GlobalAction]
)

object CCConfig {
  def load: RManaged[PartialEnv, CCConfig] =
    for {
      file   <- envConfigFile.orElse(homeConfigFile).catchAll(_ => UIO(new File("application.conf"))).toManaged_
      _      <- log.debug(s"Loading config file at ${file.getAbsolutePath}").toManaged_
      config <- load(file)
    } yield config

  def load(file: File): RManaged[PartialEnv, CCConfig] =
    for {
      config   <- effectBlocking(ConfigFactory.parseFile(file)).toManaged_
      ccConfig <- loadFrom(config)
    } yield ccConfig

  def loadFrom(config: TypesafeConfig): RManaged[PartialEnv, CCConfig] =
    for {
      commands       <- CommandPlugin.loadAll(config, "commands")
      aliases        <- ZManaged.fromEither(config.as[Map[String, List[String]]]("aliases"))
      generalConfig  <- ZManaged.fromEither(config.as[GeneralConfig]("general"))
      displayConfig  <- ZManaged.fromEither(config.as[DisplayConfig]("display"))
      keyboardConfig <- ZManaged.fromEither(config.as[KeyboardConfig]("keyboard"))
      globalActions  <- ZManaged.fromEither(config.get[Vector[GlobalAction]]("globalActions"))
    } yield CCConfig(
      commands.toVector,
      aliases,
      generalConfig,
      displayConfig,
      keyboardConfig,
      globalActions.filter(_.shortcut.nonEmpty)
    )

  def validateConfig: ZIO[Blocking with Logging with System, Throwable, Unit] =
    for {
      file <- envConfigFile.orElse(homeConfigFile).catchAll(_ => UIO(new File("application.conf")))
      _    <- log.debug(s"Validating config file at ${file.getAbsolutePath}")
      _    <- effectBlocking(ConfigFactory.parseFile(file))
    } yield ()

  private def envConfigFile: ZIO[System, Option[SecurityException], File] =
    for {
      configPathOpt <- zio.system.env("COMMAND_CENTER_CONFIG_PATH").asSomeError
      configPath    <- ZIO.fromOption(configPathOpt)
      file           = new File(configPath)
    } yield if (file.isDirectory) new File(file, "application.conf") else file

  private def homeConfigFile: ZIO[System, Option[Throwable], File] =
    for {
      userHome     <- zio.system.propertyOrElse("user.home", "").asSomeError
      potentialFile = new File(userHome, "/.command-center/application.conf")
      file         <- ZIO.fromOption(Option.when(potentialFile.exists())(potentialFile))
    } yield file
}

final case class GeneralConfig(debounceDelay: Duration)

object GeneralConfig {
  implicit val decoder: Decoder[GeneralConfig] =
    Decoder.instance { c =>
      for {
        debounceDelay <- c.get[scala.concurrent.duration.Duration]("debounceDelay")
      } yield GeneralConfig(debounceDelay.toNanos.nanos)
    }
}

final case class DisplayConfig(width: Int, maxHeight: Int, opacity: Float, fonts: List[Font])

object DisplayConfig {
  implicit val decoder: Decoder[DisplayConfig] =
    Decoder.forProduct4("width", "maxHeight", "opacity", "fonts")(DisplayConfig.apply)
}

final case class KeyboardConfig(openShortcut: KeyboardShortcut, suspendShortcut: Option[String])

object KeyboardConfig {
  implicit val decoder: Decoder[KeyboardConfig] = Decoder.instance { c =>
    for {
      openShortcut    <-
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
  case object MinimizeWindow                      extends GlobalActionId
  case object MaximizeWindow                      extends GlobalActionId
  case object ToggleMaximizeWindow                extends GlobalActionId
  case object CenterWindow                        extends GlobalActionId
  case object MoveToNextScreen                    extends GlobalActionId
  case object MoveToPreviousScreen                extends GlobalActionId
  case object ResizeToScreenSize                  extends GlobalActionId
  case object ResizeFullHeightMaintainAspectRatio extends GlobalActionId
  case object CycleWindowSizeLeft                 extends GlobalActionId
  case object CycleWindowSizeRight                extends GlobalActionId
  case object CycleWindowSizeTop                  extends GlobalActionId
  case object CycleWindowSizeBottom               extends GlobalActionId

  lazy val values: IndexedSeq[GlobalActionId] = findValues
}

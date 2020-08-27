package commandcenter

import java.awt.Font
import java.io.File

import com.typesafe.config.{ Config, ConfigFactory }
import commandcenter.CCRuntime.Env
import commandcenter.command.{ Command, CommandPlugin }
import commandcenter.config.ConfigParserExtensions._
import commandcenter.config.Decoders._
import commandcenter.event.KeyboardShortcut
import commandcenter.util.OS
import io.circe.Decoder
import zio.blocking._
import zio.{ RManaged, ZManaged }

import scala.util.Try

object CCConfig {
  def load: RManaged[Env, CCConfig] = {
    val configFile = envConfigFile.orElse(homeConfigFile).getOrElse(new File("application.conf"))
    load(configFile)
  }

  def load(file: File): RManaged[Env, CCConfig] =
    for {
      config   <- effectBlocking(ConfigFactory.parseFile(file)).toManaged_
      ccConfig <- loadFrom(config)
    } yield ccConfig

  def loadFrom(config: Config): RManaged[Env, CCConfig] =
    for {
      commands       <- CommandPlugin.loadAll(config, "commands")
      aliases        <- ZManaged.fromEither(config.as[Map[String, List[String]]]("aliases"))
      displayConfig  <- ZManaged.fromEither(config.as[DisplayConfig]("display"))
      keyboardConfig <- ZManaged.fromEither(config.as[KeyboardConfig]("keyboard"))
    } yield CCConfig(commands.toVector, aliases, displayConfig, keyboardConfig)

  private def envConfigFile: Option[File] =
    sys.env
      .get("COMMAND_CENTER_CONFIG_PATH")
      .map { path =>
        val file = new File(path)
        if (file.isDirectory) new File(file, "application.conf") else file
      }

  private def homeConfigFile: Option[File] = {
    val userHome = Try(System.getProperty("user.home")).toOption.getOrElse("")

    Option(new File(userHome, "/.command-center/application.conf")).filter(_.exists)
  }
}

final case class CCConfig(
  commands: Vector[Command[Any]],
  aliases: Map[String, List[String]],
  display: DisplayConfig,
  keyboard: KeyboardConfig
)

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

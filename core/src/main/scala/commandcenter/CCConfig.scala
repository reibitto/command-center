package commandcenter

import java.awt.Font
import java.io.File

import com.typesafe.config.{ Config, ConfigFactory }
import commandcenter.command.{ Command, CommandPlugin }
import commandcenter.config.Decoders._
import io.circe.Decoder
import io.circe.config.syntax._
import zio.blocking._
import zio.{ RIO, Task }

import scala.util.Try

object CCConfig {
  def load: RIO[Blocking, CCConfig] = {
    val configFile = envConfigFile.orElse(homeConfigFile).getOrElse(new File("application.conf"))
    load(configFile)
  }

  def load(file: File): RIO[Blocking, CCConfig] =
    for {
      config   <- effectBlocking(ConfigFactory.parseFile(file))
      ccConfig <- loadFrom(config)
    } yield ccConfig

  def loadFrom(config: Config): Task[CCConfig] =
    for {
      commands      <- CommandPlugin.load(config, "commands")
      aliases       <- Task.fromEither(config.as[Map[String, List[String]]]("aliases"))
      displayConfig <- Task.fromEither(config.as[DisplayConfig]("display"))
    } yield CCConfig(commands.toVector, aliases, displayConfig)

  private def envConfigFile: Option[File] =
    sys.env
      .get("COMMAND_CENTER_CONFIG_PATH")
      .map { path =>
        val file = new File(path)
        if (file.isDirectory) new File(file, "application.conf") else file
      }

  private def homeConfigFile: Option[File] = {
    val userHome = Try(System.getProperty("user.home")).toOption.getOrElse("")

    Option(new File(userHome, "/.commandcenter/application.conf")).filter(_.exists)
  }
}

final case class CCConfig(
  commands: Vector[Command[Any]],
  aliases: Map[String, List[String]],
  display: DisplayConfig
)

final case class DisplayConfig(width: Int, maxHeight: Int, opacity: Float, fonts: List[Font])

object DisplayConfig {
  implicit val decoder: Decoder[DisplayConfig] =
    Decoder.forProduct4("width", "maxHeight", "opacity", "fonts")(DisplayConfig.apply)
}

final case class KeyboardConfig()

package commandcenter.config

import com.typesafe.config.*
import commandcenter.command.CommandPluginError
import io.circe.{Decoder, Json}
import io.circe.config.parser
import zio.{IO, ZIO}

import scala.jdk.CollectionConverters.*

trait ConfigParserExtensions {

  implicit class ConfigExtension(val config: Config) {

    def convertValueUnsafe(value: ConfigValue): Json =
      value match {
        case obj: ConfigObject =>
          Json.fromFields(obj.asScala.view.mapValues(convertValueUnsafe).toMap)

        case list: ConfigList =>
          Json.fromValues(list.asScala.map(convertValueUnsafe))

        case _ =>
          (value.valueType, value.unwrapped) match {
            case (ConfigValueType.NULL, _)                             => Json.Null
            case (ConfigValueType.NUMBER, int: java.lang.Integer)      => Json.fromInt(int)
            case (ConfigValueType.NUMBER, long: java.lang.Long)        => Json.fromLong(long)
            case (ConfigValueType.BOOLEAN, boolean: java.lang.Boolean) => Json.fromBoolean(boolean)
            case (ConfigValueType.STRING, str: String)                 => Json.fromString(str)

            case (ConfigValueType.NUMBER, double: java.lang.Double) =>
              Json.fromDouble(double).getOrElse {
                throw new NumberFormatException(s"Invalid numeric string ${value.render}")
              }

            case (valueType, _) =>
              throw new RuntimeException(s"No conversion for $valueType with value $value")
          }
      }

    /**
     * Read config settings into the specified type.
     */
    def as[A: Decoder]: Either[io.circe.Error, A] = parser.decode[A](config)

    /**
     * Read config settings at given path into the specified type.
     */
    def as[A: Decoder](path: String): Either[io.circe.Error, A] = parser.decodePath[A](config, path)

    /**
     * Get the value at given path into the specified type.
     */
    def get[A: Decoder](path: String): Either[io.circe.Error, A] = {
      val json =
        if (config.hasPath(path))
          convertValueUnsafe(config.getValue(path))
        else
          Json.Null

      implicitly[Decoder[A]].decodeJson(json)
    }

    def getZIO[A: Decoder](path: String): IO[CommandPluginError, A] =
      ZIO.fromEither(get(path)).mapError(CommandPluginError.UnexpectedException)

  }
}

object ConfigParserExtensions extends ConfigParserExtensions

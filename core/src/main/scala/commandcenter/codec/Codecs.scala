package commandcenter.codec

import java.time.ZoneId
import java.util.Locale

import cats.syntax.either._
import io.circe.{ Decoder, Encoder, Json }

object Codecs {
  implicit val localeDecoder: Decoder[Locale] = Decoder.decodeString.emap { s =>
    Either.catchNonFatal(Locale.forLanguageTag(s)).leftMap(_.getMessage)
  }

  implicit val localeEncoder: Encoder[Locale] = (locale: Locale) => Json.fromString(locale.toLanguageTag)

  implicit val zoneIdDecoder: Decoder[ZoneId] = Decoder.decodeString.emap { s =>
    Either.catchNonFatal(ZoneId.of(s)).leftMap(_.getMessage)
  }

  implicit val zoneIdEncoder: Encoder[ZoneId] = (locale: ZoneId) => Json.fromString(locale.getId)
}

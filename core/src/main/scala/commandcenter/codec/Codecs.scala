package commandcenter.codec

import cats.syntax.either.*
import io.circe.*

import java.time.ZoneId
import java.util.Locale

object Codecs {

  implicit val localeDecoder: Decoder[Locale] = Decoder.decodeString.emap { s =>
    Either.catchNonFatal(Locale.forLanguageTag(s)).leftMap(_.getMessage)
  }

  implicit val localeEncoder: Encoder[Locale] = (locale: Locale) => Json.fromString(locale.toLanguageTag)

  implicit val zoneIdDecoder: Decoder[ZoneId] = Decoder.decodeString.emap { s =>
    Either.catchNonFatal(ZoneId.of(s)).leftMap(_.getMessage)
  }

  implicit val zoneIdEncoder: Encoder[ZoneId] = (locale: ZoneId) => Json.fromString(locale.getId)

  def decodeSumBySoleKey[A](f: PartialFunction[(String, ACursor), Decoder.Result[A]]): Decoder[A] = {
    def keyErr = "Expected a single key indicating the subtype"
    Decoder.instance { c =>
      c.keys match {
        case Some(it) =>
          it.toList match {
            case singleKey :: Nil =>
              val arg = (singleKey, c.downField(singleKey))
              def fail = Left(DecodingFailure("Unknown subtype: " + singleKey, c.history))
              f.applyOrElse(arg, (_: (String, ACursor)) => fail)
            case Nil  => Left(DecodingFailure(keyErr, c.history))
            case keys => Left(DecodingFailure(s"$keyErr, found multiple: $keys", c.history))
          }
        case None => Left(DecodingFailure(keyErr, c.history))
      }
    }
  }
}

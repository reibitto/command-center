package commandcenter.config

import io.circe.{Decoder, DecodingFailure}

import java.awt.Font
import java.io.File
import java.nio.file.{Path, Paths}
import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.util.Try

object Decoders {

  implicit val fontDecoder: Decoder[Font] =
    Decoder.instance { c =>
      for {
        name      <- c.get[String]("name")
        size      <- c.get[Int]("size")
        style     <- c.get[Option[String]]("style")
        fontStyle <- style.map(_.trim.toLowerCase) match {
                       case None | Some("plain")                => Right(Font.PLAIN)
                       case Some("bold")                        => Right(Font.BOLD)
                       case Some("italic")                      => Right(Font.ITALIC)
                       case Some("bold italic" | "italic bold") => Right(Font.BOLD | Font.ITALIC)
                       case Some(other)                         =>
                         Left(DecodingFailure(s"Unknown font style: $other", c.history))
                     }
      } yield new Font(name, fontStyle, size)
    }

  implicit val pathDecoder: Decoder[Path] = Decoder.decodeString.emap { s =>
    Try(Paths.get(s)).toEither.left.map(_.getMessage)
  }

  implicit val fileDecoder: Decoder[File] = Decoder.decodeString.emap { s =>
    Try(new File(s)).toEither.left.map(_.getMessage)
  }

  implicit val scalaDurationDecoder: Decoder[scala.concurrent.duration.Duration] = Decoder.decodeString.emap { s =>
    Try {
      scala.concurrent.duration.Duration(s)
    }.toEither.left.map(_.getMessage)
  }

  implicit val dateTimeFormatterDecoder: Decoder[DateTimeFormatter] = Decoder.decodeString.emap { s =>
    if (s.equalsIgnoreCase("short")) Right(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
    else if (s.equalsIgnoreCase("medium")) Right(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
    else if (s.equalsIgnoreCase("long")) Right(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG))
    else if (s.equalsIgnoreCase("full")) Right(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL))
    else Try(DateTimeFormatter.ofPattern(s)).toEither.left.map(_.getMessage)
  }
}

package commandcenter.config

import io.circe.Decoder

import java.awt.Font
import java.nio.file.{ Path, Paths }
import scala.util.Try

object Decoders {
  implicit val fontDecoder: Decoder[Font] =
    Decoder.instance { c =>
      for {
        name <- c.get[String]("name")
        size <- c.get[Int]("size")
      } yield new Font(name, Font.PLAIN, size) // TODO: Also support style
    }

  implicit val pathDecoder: Decoder[Path] = Decoder.decodeString.emap { s =>
    Try(Paths.get(s)).toEither.left.map(_.getMessage)
  }

  implicit val scalaDurationDecoder: Decoder[scala.concurrent.duration.Duration] = Decoder.decodeString.emap { s =>
    Try {
      scala.concurrent.duration.Duration(s)
    }.toEither.left.map(_.getMessage)
  }
}

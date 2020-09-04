package commandcenter.config

import java.awt.Font
import java.nio.file.{ Path, Paths }

import commandcenter.event.KeyboardShortcut
import io.circe.Decoder

import scala.util.Try

object Decoders {
  implicit val fontDecoder: Decoder[Font] =
    Decoder.instance { c =>
      for {
        name <- c.get[String]("name")
        size <- c.get[Int]("size")
      } yield new Font(name, Font.PLAIN, size) // TODO: Also support style
    }

  implicit val keyboardShortcutDecoder: Decoder[KeyboardShortcut] = Decoder.decodeString.emap { s =>
    KeyboardShortcut.fromString(s).toEither.left.map(_.toList.mkString("; "))
  }

  implicit val pathDecoder: Decoder[Path] = Decoder.decodeString.emap { s =>
    Try(Paths.get(s)).toEither.left.map(_.getMessage)
  }
}

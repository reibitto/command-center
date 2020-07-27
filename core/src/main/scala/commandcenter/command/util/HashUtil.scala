package commandcenter.command.util

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import cats.syntax.either._

object HashUtil {

  def hash(algorithm: String)(text: String, charset: Charset = UTF_8): Either[Throwable, String] =
    Either.catchNonFatal {
      // Note: MessageDigest is not thread-safe. It requires creating a new instance for each hash.
      val hashFunction = MessageDigest.getInstance(algorithm)

      hashFunction.digest(text.getBytes(charset)).map("%02x".format(_)).mkString
    }

}

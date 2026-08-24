package commandcenter.util

import zio.*

import java.io.FileNotFoundException
import java.nio.charset.{Charset, StandardCharsets}

object ClasspathResource {

  def loadText(resourcePath: String, charset: Charset = StandardCharsets.UTF_8): Task[String] =
    ZIO.attemptBlockingIO {
      val inputStream = getClass.getClassLoader.getResourceAsStream(resourcePath)

      if (inputStream == null) throw new FileNotFoundException(s"No such resource: '$resourcePath'")

      try
        new String(inputStream.readAllBytes(), charset)
      finally
        inputStream.close()
    }
}

package commandcenter.command

import java.nio.charset.Charset

import cats.data.Validated
import com.monovore.decline.Opts

import scala.util.Try

object CommonOpts {
  val stringArg: Opts[String] = Opts.argument[String]()

  val encodingOpt: Opts[Charset] = Opts
    .option[String]("charset", "charset (e.g. utf8)", "c")
    .withDefault("UTF-8")
    .mapValidated { charset =>
      Try(Charset.forName(charset)).fold(t => Validated.invalidNel(s"${t.getMessage}"), Validated.Valid(_))
    }
}

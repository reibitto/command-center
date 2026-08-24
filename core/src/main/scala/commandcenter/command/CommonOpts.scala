package commandcenter.command

import cats.data.Validated
import com.monovore.decline.Opts

import java.nio.charset.Charset
import scala.util.Try

object CommonOpts {
  def stringArg(metavar: String = "text"): Opts[String] = Opts.argument[String](metavar)

  val encodingOpt: Opts[Charset] = Opts
    .option[String]("charset", "charset (e.g. utf8)", "c")
    .withDefault("UTF-8")
    .mapValidated { charset =>
      Try(Charset.forName(charset)).fold(t => Validated.invalidNel(s"${t.getMessage}"), Validated.Valid(_))
    }
}

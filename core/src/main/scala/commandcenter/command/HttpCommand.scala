package commandcenter.command

import com.typesafe.config.Config
import commandcenter.codec.Codecs.localeDecoder
import commandcenter.command.CommandError.*
import commandcenter.event.KeyboardShortcut
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import commandcenter.Sttp
import fansi.{Color, Str}
import io.circe.Json
import sttp.client3.basicRequest
import sttp.model.{Method, Uri}
import sttp.model.internal.Rfc3986
import zio.*

import java.util.Locale

final case class HttpCommand(
    title: String,
    urlTemplate: String,
    method: Method,
    body: Option[String],
    contentType: Option[String],
    override val commandNames: List[String],
    override val locales: Set[Locale],
    override val shortcuts: Set[KeyboardShortcut]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.HttpCommand

  val encodeQuery: String => String =
    Rfc3986.encode(Rfc3986.Query -- Set('&', '='), spaceAsPlus = false, encodePlus = true)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] = {
    def prefixPreview: ZIO[Env, CommandError, PreviewResults[Unit]] =
      for {
        input <- ZIO.fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty)).orElseFail(CommandError.NotApplicable)
        localeBoost = if (locales.contains(input.context.locale)) 2 else 1
      } yield PreviewResults.one(
        Preview.unit
          .score(Scores.veryHigh * localeBoost)
          .onRun(executeHttpRequest(input.rest))
          .rendered(Renderer.renderDefault(title, Str("Execute ") ++ Color.Magenta(input.rest)))
      )

    def rawInputPreview: ZIO[Env, CommandError, PreviewResults[Unit]] =
      if (searchInput.input.isEmpty || !(locales.isEmpty || locales.contains(searchInput.context.locale)))
        ZIO.fail(NotApplicable)
      else {
        ZIO.succeed(
          PreviewResults.one(
            Preview.unit
              .score(Scores.high * 0.35)
              .onRun(executeHttpRequest(searchInput.input))
              .rendered(
                Renderer.renderDefault(title, Str("Execute ") ++ Color.Magenta(searchInput.input))
              )
          )
        )
      }

    prefixPreview.orElse(rawInputPreview)
  }

  private def executeHttpRequest(query: String): RIO[Sttp, Unit] = {
    val url = urlTemplate.replace("{query}", encodeQuery(query))

    for {
      uri <- ZIO.fromEither(Uri.parse(url)).mapError(s => new Exception(s"The generated URL isn't valid: $s"))
      request = (body, contentType) match {
                  case (Some(b), Some(c)) =>
                    basicRequest.method(method, uri).contentType(c).body(transformBody(query)(b))

                  case (Some(b), None) =>
                    basicRequest.method(method, uri).body(transformBody(query)(b))

                  case _ =>
                    basicRequest.method(method, uri)
                }
      response <- Sttp.send(request)
      _        <- ZIO.foreachDiscard(response.body.left.toOption) { error =>
             ZIO.logWarning(s"`$title` HTTP command failed: $error")
           }
    } yield ()
  }

  private def transformBody(query: String)(body: String): String =
    body
      .replace("{query}", query)
      .replace("{query:json}", Json.fromString(query).noSpaces)
}

object HttpCommand extends CommandPlugin[HttpCommand] {

  def make(config: Config): IO[CommandPluginError, HttpCommand] =
    for {
      title       <- config.getZIO[String]("title")
      urlTemplate <- config.getZIO[String]("urlTemplate")
      methodOpt   <- config.getZIO[Option[String]]("method")
      method      <- methodOpt match {
                  case Some(method) =>
                    ZIO.fromEither(Method.safeApply(method)).catchAll { e =>
                      ZIO
                        .logWarning(s"$title HttpCommand method is misconfigured so defaulting to GET: $e")
                        .as(Method.GET)
                    }

                  case None => ZIO.succeed(Method.GET)
                }
      body         <- config.getZIO[Option[String]]("body")
      contentType  <- config.getZIO[Option[String]]("contentType")
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
      locales      <- config.getZIO[Option[Set[Locale]]]("locales")
      shortcuts    <- config.getZIO[Option[Set[KeyboardShortcut]]]("shortcuts")
    } yield HttpCommand(
      title,
      urlTemplate,
      method,
      body,
      contentType,
      commandNames.getOrElse(Nil),
      locales.getOrElse(Set.empty),
      shortcuts.getOrElse(Set.empty)
    )
}

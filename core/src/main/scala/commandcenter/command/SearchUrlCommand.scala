package commandcenter.command

import com.typesafe.config.Config
import commandcenter.codec.Codecs.localeDecoder
import commandcenter.command.CommandError.*
import commandcenter.config.Decoders.*
import commandcenter.event.KeyboardShortcut
import commandcenter.util.ProcessUtil
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.Color
import fansi.Str
import sttp.model.internal.Rfc3986
import sttp.model.Uri
import zio.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Locale

final case class SearchUrlCommand(
    title: String,
    urlTemplate: String,
    override val commandNames: List[String],
    override val locales: Set[Locale],
    override val shortcuts: Set[KeyboardShortcut],
    firefoxPath: Option[Path]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.SearchUrlCommand

  val encodeQuery: String => String =
    Rfc3986.encode(Rfc3986.Query -- Set('&', '='), spaceAsPlus = false, encodePlus = true)

  def openBrowser(query: String): Task[Unit] = {
    val url = urlTemplate.replace("{query}", encodeQuery(query))
    ProcessUtil.openBrowser(url, firefoxPath)
  }

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] = {
    def prefixPreview: ZIO[Env, CommandError, PreviewResults[Unit]] =
      for {
        input <- ZIO.fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty)).orElseFail(CommandError.NotApplicable)
        localeBoost = if (locales.contains(input.context.locale)) 2 else 1
      } yield PreviewResults.one(
        Preview.unit
          .score(Scores.veryHigh * localeBoost)
          .onRun(openBrowser(input.rest))
          .rendered(Renderer.renderDefault(title, Str("Search for ") ++ Color.Magenta(input.rest)))
      )

    def rawInputPreview: ZIO[Env, CommandError, PreviewResults[Unit]] =
      if (searchInput.input.isEmpty || !(locales.isEmpty || locales.contains(searchInput.context.locale)))
        ZIO.fail(NotApplicable)
      else {
        ZIO.succeed(
          PreviewResults.one(
            Preview.unit
              .score(Scores.high * 0.35)
              .onRun(openBrowser(searchInput.input))
              .rendered(
                Renderer.renderDefault(title, Str("Search for ") ++ Color.Magenta(searchInput.input))
              )
          )
        )
      }

    prefixPreview.orElse(rawInputPreview)
  }
}

object SearchUrlCommand extends CommandPlugin[SearchUrlCommand] {

  def make(config: Config): IO[CommandPluginError, SearchUrlCommand] =
    for {
      title        <- config.getZIO[String]("title")
      urlTemplate  <- config.getZIO[String]("urlTemplate")
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
      locales      <- config.getZIO[Option[Set[Locale]]]("locales")
      shortcuts    <- config.getZIO[Option[Set[KeyboardShortcut]]]("shortcuts")
      firefoxPath  <- config.getZIO[Option[Path]]("firefoxPath")
    } yield SearchUrlCommand(
      title,
      urlTemplate,
      commandNames.getOrElse(Nil),
      locales.getOrElse(Set.empty),
      shortcuts.getOrElse(Set.empty),
      firefoxPath
    )
}

package commandcenter.command

import com.typesafe.config.Config
import commandcenter.codec.Codecs.localeDecoder
import commandcenter.command.CommandError.*
import commandcenter.event.KeyboardShortcut
import commandcenter.util.ProcessUtil
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

final case class SearchUrlCommand(
  title: String,
  urlTemplate: String,
  override val commandNames: List[String] = List.empty,
  override val locales: Set[Locale] = Set.empty,
  override val shortcuts: Set[KeyboardShortcut] = Set.empty
) extends Command[Unit] {
  val commandType: CommandType = CommandType.SearchUrlCommand

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] = {
    def prefixPreview: ZIO[Env, CommandError, PreviewResults[Unit]] =
      for {
        input <- ZIO.fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty)).orElseFail(CommandError.NotApplicable)
        url = urlTemplate.replace("{query}", URLEncoder.encode(input.rest, StandardCharsets.UTF_8))
        localeBoost = if (locales.contains(input.context.locale)) 2 else 1
      } yield PreviewResults.one(
        Preview.unit
          .score(Scores.high * localeBoost)
          .onRun(ProcessUtil.openBrowser(url))
          .rendered(Renderer.renderDefault(title, fansi.Str("Search for ") ++ fansi.Color.Magenta(input.rest)))
      )

    def rawInputPreview: ZIO[Env, CommandError, PreviewResults[Unit]] =
      if (searchInput.input.isEmpty || !(locales.isEmpty || locales.contains(searchInput.context.locale)))
        ZIO.fail(NotApplicable)
      else {
        val url = urlTemplate.replace("{query}", URLEncoder.encode(searchInput.input, StandardCharsets.UTF_8))

        UIO(
          PreviewResults.one(
            Preview.unit
              .score(Scores.high * 0.35)
              .onRun(ProcessUtil.openBrowser(url))
              .rendered(
                Renderer.renderDefault(title, fansi.Str("Search for ") ++ fansi.Color.Magenta(searchInput.input))
              )
          )
        )
      }

    prefixPreview.orElse(rawInputPreview)
  }
}

object SearchUrlCommand extends CommandPlugin[SearchUrlCommand] {

  def make(config: Config): Managed[CommandPluginError, SearchUrlCommand] =
    for {
      title        <- config.getManaged[String]("title")
      urlTemplate  <- config.getManaged[String]("urlTemplate")
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
      locales      <- config.getManaged[Option[Set[Locale]]]("locales")
      shortcuts    <- config.getManaged[Option[Set[KeyboardShortcut]]]("shortcuts")
    } yield SearchUrlCommand(
      title,
      urlTemplate,
      commandNames.getOrElse(Nil),
      locales.getOrElse(Set.empty),
      shortcuts.getOrElse(Set.empty)
    )
}

package commandcenter.command

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.codec.Codecs.localeDecoder
import commandcenter.command.CommandError._
import commandcenter.util.ProcessUtil
import commandcenter.view.DefaultView
import zio._

final case class SearchUrlCommand(
  title: String,
  urlTemplate: String,
  override val commandNames: List[String] = List.empty,
  override val locales: Set[Locale] = Set.empty
) extends Command[Unit] {
  val commandType: CommandType = CommandType.SearchUrlCommand

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] = {
    def prefixPreview: ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
      for {
        input      <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
        _          <- ZIO.fail(NotApplicable).when(input.rest.isEmpty)
        url         = urlTemplate.replace("{query}", URLEncoder.encode(input.rest, StandardCharsets.UTF_8))
        localeBoost = if (locales.contains(input.context.locale)) 2 else 1
      } yield List(
        Preview.unit
          .score(Scores.high * localeBoost)
          .onRun(ProcessUtil.openBrowser(url))
          .view(DefaultView(title, fansi.Str("Search for ") ++ fansi.Color.Magenta(input.rest)))
      )

    def rawInputPreview: ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
      if (searchInput.input.isEmpty || !(locales.isEmpty || locales.contains(searchInput.context.locale)))
        IO.fail(NotApplicable)
      else {
        val url = urlTemplate.replace("{query}", URLEncoder.encode(searchInput.input, StandardCharsets.UTF_8))

        UIO(
          List(
            Preview.unit
              .score(Scores.high * 0.35)
              .onRun(ProcessUtil.openBrowser(url))
              .view(DefaultView(title, fansi.Str("Search for ") ++ fansi.Color.Magenta(searchInput.input)))
          )
        )
      }

    prefixPreview.orElse(rawInputPreview)
  }
}

object SearchUrlCommand extends CommandPlugin[SearchUrlCommand] {
  def make(config: Config): TaskManaged[SearchUrlCommand] =
    ZManaged.fromEither(
      for {
        title        <- config.get[String]("title")
        urlTemplate  <- config.get[String]("urlTemplate")
        commandNames <- config.get[Option[List[String]]]("commandNames")
        locales      <- config.get[Option[Set[Locale]]]("locales")
      } yield SearchUrlCommand(title, urlTemplate, commandNames.getOrElse(List.empty), locales.getOrElse(Set.empty))
    )
}

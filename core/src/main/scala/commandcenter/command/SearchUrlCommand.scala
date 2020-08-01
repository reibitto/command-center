package commandcenter.command

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import commandcenter.CCRuntime.Env
import commandcenter.codec.Codecs.localeDecoder
import commandcenter.command.CommandError._
import commandcenter.util.ProcessUtil
import commandcenter.view.DefaultView
import io.circe.Decoder
import zio.{ IO, UIO, ZIO }

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
              .score(Scores.high * 0.8)
              .onRun(ProcessUtil.openBrowser(url))
              .view(DefaultView(title, fansi.Str("Search for ") ++ fansi.Color.Magenta(searchInput.input)))
          )
        )
      }

    prefixPreview.orElse(rawInputPreview)
  }
}

object SearchUrlCommand extends CommandPlugin[SearchUrlCommand] {
  implicit val decoder: Decoder[SearchUrlCommand] =
    Decoder.instance { c =>
      for {
        title        <- c.get[String]("title")
        urlTemplate  <- c.get[String]("urlTemplate")
        commandNames <- c.get[Option[List[String]]]("commandNames")
        locales      <- c.get[Option[Set[Locale]]]("locales")
      } yield SearchUrlCommand(title, urlTemplate, commandNames.getOrElse(List.empty), locales.getOrElse(Set.empty))
    }
}

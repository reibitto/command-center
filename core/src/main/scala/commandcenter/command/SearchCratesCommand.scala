package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.SearchCratesCommand.CrateResult
import commandcenter.tools
import io.circe.{ Decoder, Json }
import sttp.client._
import sttp.client.circe._
import sttp.client.httpclient.zio._
import zio.{ IO, TaskManaged, ZIO, ZManaged }

import java.time.OffsetDateTime

final case class SearchCratesCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.SearchCratesCommand
  val title: String            = "Crates"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[Unit]]] =
    for {
      input    <- ZIO.fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty)).orElseFail(CommandError.NotApplicable)
      request   = basicRequest
                    .get(uri"https://crates.io/api/v1/crates?page=1&per_page=20&q=${input.rest}")
                    .response(asJson[Json])
      response <- SttpClient
                    .send(request)
                    .map(_.body)
                    .absolve
                    .mapError(CommandError.UnexpectedException)
      results  <- IO.fromEither(
                    response.hcursor.downField("crates").as[List[CrateResult]]
                  ).mapError(CommandError.UnexpectedException)
    } yield results.map { result =>
      Preview.unit
        .onRun(tools.setClipboard(result.render))
        .score(Scores.high(input.context))
        .view(result.renderColored)
    }
}

object SearchCratesCommand extends CommandPlugin[SearchCratesCommand] {
  final case class CrateResult(
    id: String,
    name: String,
    description: String,
    createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
    downloads: Int,
    recentDownloads: Int,
    maxVersion: String
  ) {
    def descriptionSanitized: String = description.replace("\n", "").trim

    def render: String =
      s"$name = $maxVersion"

    def renderColored: fansi.Str =
      fansi.Str.join(
        fansi.Color.Green(name),
        fansi.Color.LightGray(" = "),
        fansi.Color.Cyan(maxVersion),
        fansi.Color.LightGray(s" # $descriptionSanitized")
      )
  }

  object CrateResult {
    implicit val decoder: Decoder[CrateResult] =
      Decoder.forProduct8(
        "id",
        "name",
        "description",
        "created_at",
        "updated_at",
        "downloads",
        "recent_downloads",
        "max_version"
      )(
        CrateResult.apply
      )
  }

  def make(config: Config): TaskManaged[SearchCratesCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield SearchCratesCommand(commandNames.getOrElse(List("crate", "crates")))
    )
}

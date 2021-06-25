package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.SearchCratesCommand.CrateResults
import commandcenter.tools.Tools
import io.circe.Decoder
import sttp.client._
import sttp.client.circe._
import sttp.client.httpclient.zio._
import zio.stream.ZStream
import zio.{ Chunk, TaskManaged, ZIO, ZManaged }

import java.time.OffsetDateTime

final case class SearchCratesCommand(commandNames: List[String]) extends Command[Unit] {
  val commandType: CommandType = CommandType.SearchCratesCommand
  val title: String            = "Crates"
  val pageSize: Int            = 10

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input       <- ZIO.fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty)).orElseFail(CommandError.NotApplicable)
      cratesStream = ZStream.paginateChunkM(1) { page =>
                       val request = basicRequest
                         .get(uri"https://crates.io/api/v1/crates?page=$page&per_page=$pageSize&q=${input.rest}")
                         .response(asJson[CrateResults])

                       SttpClient
                         .send(request)
                         .map(_.body)
                         .absolve
                         .bimap(CommandError.UnexpectedException, r => (r.crates, r.meta.nextPage.map(_ => page + 1)))
                     }
    } yield PreviewResults.paginated(
      cratesStream.map { result =>
        Preview.unit
          .onRun(Tools.setClipboard(result.render))
          .score(Scores.high(input.context))
          .view(result.renderColored)
      },
      pageSize
    )
}

object SearchCratesCommand extends CommandPlugin[SearchCratesCommand] {
  final case class CrateResults(crates: Chunk[CrateResult], meta: MetaResult)

  object CrateResults {
    implicit val decoder: Decoder[CrateResults] =
      Decoder.forProduct2("crates", "meta")(CrateResults.apply)
  }

  final case class MetaResult(total: Int, nextPage: Option[String])

  object MetaResult {
    implicit val decoder: Decoder[MetaResult] =
      Decoder.forProduct2("total", "next_page")(MetaResult.apply)
  }

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
      s"""$name = "$maxVersion""""

    def renderColored: fansi.Str =
      fansi.Str.join(
        fansi.Color.Cyan(name),
        fansi.Color.LightGray(" = "),
        fansi.Color.Green(s""""$maxVersion""""),
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
      )(CrateResult.apply)
  }

  def make(config: Config): TaskManaged[SearchCratesCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield SearchCratesCommand(commandNames.getOrElse(List("crate", "crates")))
    )
}

package commandcenter.command

import java.time.{ Instant, LocalDate, ZoneId }

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.SearchMavenCommand.{ BucketedMavenArtifact, MavenArtifact }
import commandcenter.tools
import commandcenter.util.Orderings
import io.circe.{ Decoder, Json }
import sttp.client._
import sttp.client.circe._
import sttp.client.httpclient.zio._
import zio.{ IO, TaskManaged, ZIO, ZManaged }

import scala.math.Ordering
import scala.util.matching.Regex

final case class SearchMavenCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.SearchMavenCommand
  val title: String            = "Maven"

  val scalaArtifactRegex: Regex = "(.+?)(_[02].\\d+)".r

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input          <- ZIO.fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty)).orElseFail(CommandError.NotApplicable)
      request         = basicRequest
                          .get(uri"https://search.maven.org/solrsearch/select?q=${input.rest}&rows=100&wt=json")
                          .response(asJson[Json])
      response       <- SttpClient
                          .send(request)
                          .map(_.body)
                          .absolve
                          .mapError(CommandError.UnexpectedException)
      artifacts      <- IO.fromEither(
                          response.hcursor.downField("response").downField("docs").as[List[MavenArtifact]]
                        ).mapError(CommandError.UnexpectedException)
      scoredArtifacts = bucket(artifacts, Some(input.rest)).take(20)
    } yield PreviewResults.fromIterable(scoredArtifacts.map { artifact =>
      val renderedCoordinates = artifact.render

      Preview(renderedCoordinates)
        .onRun(tools.setClipboard(renderedCoordinates))
        .score(Scores.high(input.context))
        .view(artifact.renderColored)
    })

  private def bucket(mavenArtifacts: List[MavenArtifact], searchTerm: Option[String]): List[BucketedMavenArtifact] =
    mavenArtifacts.map { m =>
      val (baseArtifact, isScala) = m.artifactId match {
        case scalaArtifactRegex(scalaArtifact, _) => (scalaArtifact, true)
        case javaArtifact                         => (javaArtifact, false)
      }

      val date = LocalDate.ofInstant(m.timestamp, ZoneId.systemDefault())

      BucketedMavenArtifact(baseArtifact, date, m, isScala)
    }.distinctBy(_.artifactBase)
      .sortBy { b =>
        // Boost exact match to the top
        val score = if (searchTerm.contains(b.artifactBase)) 1 else 0

        (score, b.date, b.artifact.versionCount)
      }(Ordering.Tuple3(Ordering.Int.reverse, Orderings.LocalDateOrdering.reverse, Ordering.Int.reverse))
}

object SearchMavenCommand extends CommandPlugin[SearchMavenCommand] {
  final case class MavenArtifact(
    groupId: String,
    artifactId: String,
    version: String,
    timestamp: Instant,
    versionCount: Int
  )

  object MavenArtifact {
    implicit val decoder: Decoder[MavenArtifact] = Decoder.instance { c =>
      for {
        groupId      <- c.get[String]("g")
        artifactId   <- c.get[String]("a")
        version      <- c.get[String]("latestVersion")
        timestamp    <- c.get[Long]("timestamp").map(Instant.ofEpochMilli)
        versionCount <- c.get[Int]("versionCount")
      } yield MavenArtifact(groupId, artifactId, version, timestamp, versionCount)
    }
  }

  final case class BucketedMavenArtifact(
    artifactBase: String,
    date: LocalDate,
    artifact: MavenArtifact,
    isScala: Boolean
  ) {
    def render: String = {
      val groupSeparator = if (isScala) "%%" else "%"

      s""""${artifact.groupId}" $groupSeparator "$artifactBase" % "${artifact.version}""""
    }

    def renderColored: fansi.Str = {
      val groupSeparator = if (isScala) " %% " else " % "

      fansi.Str.join(
        fansi.Color.Green(artifact.groupId),
        fansi.Color.LightGray(groupSeparator),
        fansi.Color.Green(artifactBase),
        fansi.Color.LightGray(" % "),
        fansi.Color.Cyan(artifact.version)
      )
    }
  }

  def make(config: Config): TaskManaged[SearchMavenCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield SearchMavenCommand(commandNames.getOrElse(List("maven", "mvn")))
    )
}

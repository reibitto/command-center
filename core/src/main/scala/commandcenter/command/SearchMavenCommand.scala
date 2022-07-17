package commandcenter.command

import com.typesafe.config.Config
import commandcenter.command.SearchMavenCommand.{BucketedMavenArtifact, MavenArtifact}
import commandcenter.tools.Tools
import commandcenter.util.Orderings
import commandcenter.CCRuntime.Env
import commandcenter.HttpClient
import io.circe.{Decoder, Json}
import sttp.client3.*
import sttp.client3.circe.*
import zio.managed.*
import zio.ZIO

import java.time.{Instant, LocalDate, ZoneId}
import scala.util.matching.Regex

final case class SearchMavenCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.SearchMavenCommand
  val title: String = "Maven"

  val scalaArtifactRegex: Regex = "(.+?)(_[02].\\d+)".r

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[String]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty)).orElseFail(CommandError.NotApplicable)
      request = basicRequest
                  .get(uri"https://search.maven.org/solrsearch/select?q=${input.rest}&rows=100&wt=json")
                  .response(asJson[Json])
      response <- request.send(HttpClient.backend)
                    .map(_.body)
                    .absolve
                    .mapError(CommandError.UnexpectedException)
      artifacts <- ZIO
                     .fromEither(
                       response.hcursor.downField("response").downField("docs").as[List[MavenArtifact]]
                     )
                     .mapError(CommandError.UnexpectedException)
      scoredArtifacts = bucket(artifacts, Some(input.rest)).take(20)
    } yield PreviewResults.fromIterable(scoredArtifacts.map { artifact =>
      val renderedCoordinates = artifact.render

      Preview(renderedCoordinates)
        .onRun(Tools.setClipboard(renderedCoordinates))
        .score(Scores.high(input.context))
        .renderedAnsi(artifact.renderColored)
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

      fansi.Str(
        fansi.Color.Green(artifact.groupId),
        fansi.Color.LightGray(groupSeparator),
        fansi.Color.Green(artifactBase),
        fansi.Color.LightGray(" % "),
        fansi.Color.Cyan(artifact.version)
      )
    }
  }

  def make(config: Config): Managed[CommandPluginError, SearchMavenCommand] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
    } yield SearchMavenCommand(commandNames.getOrElse(List("maven", "mvn")))
}

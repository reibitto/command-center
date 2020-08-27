package commandcenter.command

import com.typesafe.config.Config
import commandcenter.CCRuntime.Env
import commandcenter.command.SearchMavenCommand.MavenArtifact
import commandcenter.tools
import commandcenter.view.DefaultView
import io.circe.{ Decoder, Json }
import sttp.client._
import sttp.client.circe._
import sttp.client.httpclient.zio._
import zio.{ IO, TaskManaged, ZIO, ZManaged }

final case class SearchMavenCommand(commandNames: List[String]) extends Command[String] {
  val commandType: CommandType = CommandType.SearchMavenCommand
  val title: String            = "Maven"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input     <- ZIO.fromOption(searchInput.asPrefixed.filter(_.rest.nonEmpty)).orElseFail(CommandError.NotApplicable)
      request    = basicRequest
                     .get(uri"https://search.maven.org/solrsearch/select?q=${input.rest}&rows=10&wt=json")
                     .response(asJson[Json])
      response  <- SttpClient
                     .send(request)
                     .map(_.body)
                     .absolve
                     .mapError(CommandError.UnexpectedException)
      artifacts <- IO
                     .fromEither(
                       response.hcursor.downField("response").downField("docs").as[List[MavenArtifact]]
                     )
                     .mapError(CommandError.UnexpectedException)
    } yield artifacts.map { artifact =>
      Preview(artifact.toString)
        .onRun(tools.setClipboard(artifact.version))
        .score(Scores.high(input.context))
        .view(
          DefaultView(
            title,
            fansi.Str.join(
              fansi.Str("Group: "),
              fansi.Color.Magenta(artifact.groupId),
              fansi.Str(" Artifact: "),
              fansi.Color.Magenta(artifact.artifactId),
              fansi.Str(" Version: "),
              fansi.Color.Magenta(artifact.version)
            )
          )
        )
    }
}

object SearchMavenCommand extends CommandPlugin[SearchMavenCommand] {
  final case class MavenArtifact(groupId: String, artifactId: String, version: String)

  implicit val artifactDecoder: Decoder[MavenArtifact] =
    Decoder.forProduct3("g", "a", "latestVersion")(MavenArtifact.apply)

  def make(config: Config): TaskManaged[SearchMavenCommand] =
    ZManaged.fromEither(
      for {
        commandNames <- config.get[Option[List[String]]]("commandNames")
      } yield SearchMavenCommand(commandNames.getOrElse(List("maven", "mvn")))
    )
}

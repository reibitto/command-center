package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.command.CommandError.NotApplicable
import commandcenter.command.SearchMavenCommand.MavenArtifact
import commandcenter.util.ProcessUtil
import commandcenter.view.DefaultView
import io.circe.{ Decoder, Json }
import sttp.client._
import sttp.client.asynchttpclient.zio._
import sttp.client.circe._
import zio.{ IO, ZIO }

final case class SearchMavenCommand() extends Command[String] {
  val command                    = "mvn"
  val commandType: CommandType   = CommandType.SearchMavenCommand
  val commandNames: List[String] = List(command)
  val title: String              = "Maven"

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, List[PreviewResult[String]]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      result <- if (input.rest.isEmpty) {
                 IO.fail(NotApplicable)
               } else {
                 val request = basicRequest
                   .get(uri"https://search.maven.org/solrsearch/select?q=${input.rest}&rows=10&wt=json")
                   .response(asJson[Json])
                 for {
                   response <- SttpClient
                                .send(request)
                                .map(_.body)
                                .absolve
                                .mapError(CommandError.UnexpectedException)
                   artifacts <- IO
                                 .fromEither(
                                   response.hcursor.downField("response").downField("docs").as[List[MavenArtifact]]
                                 )
                                 .mapError(CommandError.UnexpectedException)
                 } yield {
                   artifacts.map { artifact =>
                     Preview(artifact.toString)
                       .onRun(ProcessUtil.copyToClipboard(artifact.version))
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
               }
    } yield result
}

object SearchMavenCommand extends CommandPlugin[SearchMavenCommand] {
  final case class MavenArtifact(groupId: String, artifactId: String, version: String)

  implicit val artifactDecoder: Decoder[MavenArtifact] =
    Decoder.forProduct3("g", "a", "latestVersion")(MavenArtifact.apply)

  implicit val decoder: Decoder[SearchMavenCommand] = Decoder.const(SearchMavenCommand())
}

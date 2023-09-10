package commandcenter.command

import com.typesafe.config.Config
import commandcenter.event.KeyboardShortcut
import commandcenter.util.{OS, PowerShellScript}
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.*
import zio.cache.{Cache, Lookup}

import scala.io.Source

final case class SpeakCommand(
    commandNames: List[String],
    override val shortcuts: Set[KeyboardShortcut] = Set.empty,
    voice: String,
    cache: Cache[String, Nothing, String]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.SpeakCommand
  val title: String = "Speak"

  val speakFn =
    if (OS.os == OS.Windows)
      PowerShellScript.loadFunction2[String, String](cache)("powershell/system/speak.ps1")
    else
      (_: String, _: String) => ZIO.succeed("")

  // TODO: Support all OSes
  override val supportedOS: Set[OS] = Set(OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] = {
    val inputOpt = searchInput.asPrefixed

    for {
      speakText <- ZIO.fromOption(inputOpt).fold(_ => searchInput.input, _.rest)
      _         <- ZIO.fail(CommandError.NotApplicable).when(speakText.isEmpty)
    } yield {
      val run = for {
        _ <- speakFn(voice, speakText)
      } yield ()

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .runOption(RunOption.RemainOpen)
          .score(inputOpt.fold(Scores.hide)(input => Scores.veryHigh(input.context)))
          .rendered(Renderer.renderDefault(title, ""))
      )
    }
  }

}

object SpeakCommand extends CommandPlugin[SpeakCommand] {

  def make(config: Config): IO[CommandPluginError, SpeakCommand] =
    for {
      cache <- Cache
                 .make(
                   1024,
                   Duration.Infinity,
                   Lookup((resource: String) => ZIO.succeed(Source.fromResource(resource)).map(_.mkString))
                 )
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
      shortcuts    <- config.getZIO[Option[Set[KeyboardShortcut]]]("shortcuts")
      voice        <- config.getZIO[Option[String]]("voice")
    } yield SpeakCommand(
      commandNames.getOrElse(List("speak", "say")),
      shortcuts.getOrElse(Set.empty),
      voice.getOrElse(""),
      cache
    )

}

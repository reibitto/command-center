package commandcenter.command

import com.typesafe.config.Config
import commandcenter.cache.ZCache
import commandcenter.event.KeyboardShortcut
import commandcenter.locale.Language
import commandcenter.util.{OS, PowerShellScript}
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import zio.*
import zio.process.Command as PCommand

import java.util.Locale
import scala.io.Source

final case class SpeakCommand(
    commandNames: List[String],
    override val shortcuts: Set[KeyboardShortcut] = Set.empty,
    voice: String,
    cache: ZCache[String, String]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.SpeakCommand
  val title: String = "Speak"

  def speak(voice: String, speakText: String): ZIO[Any, Throwable, Unit] =
    OS.os match {
      case OS.Windows =>
        speakFn(voice, speakText).unit

      case OS.MacOS =>
        val voice = Language.detect(speakText) match {
          case Locale.ENGLISH  => "Samantha"
          case Locale.KOREAN   => "Yuna"
          case Locale.JAPANESE => "Kyoko"
        }

        PCommand("say", "-v", voice, speakText).exitCode.unit

      case _ => ZIO.unit

    }

  val speakFn =
    if (OS.os == OS.Windows)
      PowerShellScript.loadFunction2[String, String](cache)("system/speak.ps1")
    else
      (_: String, _: String) => ZIO.succeed("")

  // TODO: Support Linux
  override val supportedOS: Set[OS] = Set(OS.Windows, OS.MacOS)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] = {
    val inputOpt = searchInput.asPrefixed

    for {
      speakText <- ZIO.fromOption(inputOpt).fold(_ => searchInput.input, _.rest)
      _         <- ZIO.fail(CommandError.NotApplicable).when(speakText.isEmpty)
    } yield {
      val run = for {
        _ <- speak(voice, speakText)
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
      runtime <- ZIO.runtime[Any]
      cache = ZCache
                .memoizeZIO(1024, None)((resource: String) =>
                  ZIO.succeed(Some(Source.fromResource(resource)).map(_.mkString))
                )(runtime)
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

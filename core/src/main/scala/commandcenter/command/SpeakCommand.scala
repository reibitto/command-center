package commandcenter.command

import com.typesafe.config.Config
import commandcenter.cache.ZCache
import commandcenter.event.KeyboardShortcut
import commandcenter.locale.Language
import commandcenter.util.{OS, PowerShellScript}
import commandcenter.CCRuntime.Env
import zio.*
import zio.process.Command as PCommand

import java.util.Locale

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
        val macVoice =
          if (voice.nonEmpty) voice
          else
            Language.detect(speakText) match {
              case Locale.KOREAN   => "Yuna"
              case Locale.JAPANESE => "Kyoko"
              case _               => "Samantha"
            }

        PCommand("say", "-v", macVoice, speakText).exitCode.unit

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

    val speakText = inputOpt.fold(searchInput.input)(_.rest)

    ZIO
      .fail(CommandError.NotApplicable)
      .when(speakText.isEmpty)
      .as(
        PreviewResults.one(
          inputOpt
            .fold(Preview.unit.score(Scores.hide).asFallback)(input =>
              Preview.unit.score(Scores.veryHigh(input.context))
            )
            .onRun(speak(voice, speakText))
            .runOption(RunOption.RemainOpen)
        )
      )
  }

}

object SpeakCommand extends CommandPlugin[SpeakCommand] {

  def make(config: Config): IO[CommandPluginError, SpeakCommand] =
    for {
      runtime <- ZIO.runtime[Any]
      cache = ZCache.ofClasspathResources()(runtime)
      commandNames <- commandNames(config, "speak", "say")
      shortcuts    <- shortcuts(config)
      voice        <- config.getZIO("voice", "")
    } yield SpeakCommand(commandNames, shortcuts, voice, cache)

}

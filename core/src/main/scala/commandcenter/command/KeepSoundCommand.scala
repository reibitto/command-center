package commandcenter.command

import com.typesafe.config.Config
import commandcenter.util.OS
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.{Back, Str}
import zio.*
import zio.process.{Command as PCommand, Process, ProcessOutput}

import java.io.File

// Loops a near-inaudible tone via ffplay so an idle HDMI audio link doesn't sleep and clip real audio.
final case class KeepSoundCommand(
    commandNames: List[String],
    ffplayPath: String,
    frequencyHz: Int,
    volume: Double,
    processRef: Ref[Option[Process]]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.KeepSoundCommand
  val title: String = "Keep Sound Alive"

  override val supportedOS: Set[OS] = Set(OS.Windows)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input     <- ZIO.fromOption(searchInput.asKeyword).orElseFail(CommandError.NotApplicable)
      isRunning <- KeepSoundCommand.reconcile(processRef)
    } yield {
      val indicator = if (isRunning) Back.Green(" ") else Back.Red(" ")
      val label = if (isRunning) "ON" else "OFF"

      PreviewResults.one(
        Preview.unit
          .onRun(KeepSoundCommand.toggle(processRef, ffplayPath, frequencyHz, volume))
          .score(Scores.veryHigh(input.context))
          .rendered(Renderer.renderDefault(title, indicator ++ Str(s" $label")))
      )
    }
}

object KeepSoundCommand extends CommandPlugin[KeepSoundCommand] {

  def make(config: Config): ZIO[Env, CommandPluginError, KeepSoundCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
      ffplayPath   <- config.getZIO[Option[String]]("ffplayPath")
      frequencyHz  <- config.getZIO[Option[Int]]("frequencyHz")
      volume       <- config.getZIO[Option[Double]]("volume")
      processRef   <- Ref.make(Option.empty[Process])
    } yield KeepSoundCommand(
      commandNames.getOrElse(List("keepsound")),
      ffplayPath.getOrElse("ffplay"),
      frequencyHz.getOrElse(60),
      volume.getOrElse(0.0005),
      processRef
    )

  // Clears a stale ref if ffplay exited/crashed on its own, so preview never shows a stale "ON".
  private def reconcile(processRef: Ref[Option[Process]]): UIO[Boolean] =
    for {
      current <- processRef.get
      isAlive <- ZIO.foreach(current)(_.isAlive).map(_.getOrElse(false))
      _       <- processRef.set(None).when(current.isDefined && !isAlive)
    } yield isAlive

  private def toggle(
      processRef: Ref[Option[Process]],
      ffplayPath: String,
      frequencyHz: Int,
      volume: Double
  ): Task[Unit] =
    reconcile(processRef).flatMap { isRunning =>
      if (isRunning)
        processRef.get.flatMap(ZIO.foreachDiscard(_)(_.killForcibly)) *> processRef.set(None)
      else
        start(ffplayPath, frequencyHz, volume).flatMap(process => processRef.set(Some(process)))
    }

  private def start(ffplayPath: String, frequencyHz: Int, volume: Double): Task[Process] =
    PCommand(
      ffplayPath,
      "-f",
      "lavfi",
      "-i",
      s"sine=frequency=$frequencyHz",
      "-af",
      s"volume=$volume",
      "-nodisp",
      "-loglevel",
      "quiet"
    ).stdout(ProcessOutput.FileRedirect(new File("NUL")))
      .stderr(ProcessOutput.FileRedirect(new File("NUL")))
      .run
}

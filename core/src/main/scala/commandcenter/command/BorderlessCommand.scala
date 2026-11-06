package commandcenter.command

import cats.data.Validated
import com.monovore.decline
import com.monovore.decline.Opts
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.Pointer
import com.typesafe.config.Config
import commandcenter.event.KeyboardShortcut
import commandcenter.shortcuts.Shortcuts
import commandcenter.util.{OS, WindowManager, WindowStyles}
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.{Color, Str}
import zio.*

final case class BorderlessCommand(
    commandNames: List[String],
    originalStylesRef: Ref[Map[Long, WindowStyles]]
) extends Command[Unit] {
  val commandType: CommandType = CommandType.BorderlessCommand
  val title: String = "Borderless Window"

  override val supportedOS: Set[OS] = Set(OS.Windows)

  val mode = Opts
    .argument[String]("fill|strip|restore")
    .mapValidated { s =>
      BorderlessCommand.Mode.fromString(s) match {
        case Some(m) => Validated.validNel(m)
        case None    => Validated.invalidNel(s"Invalid mode `$s`. Expected one of: fill, strip, restore")
      }
    }
    .withDefault(BorderlessCommand.Mode.Fill)

  val command = decline.Command("borderless", title)(mode)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed = command.parse(input.args)
      message <- ZIO
                   .fromEither(parsed)
                   .fold(HelpMessage.formatted, m => Str("Mode: ") ++ Color.Magenta(m.description))
    } yield {
      val run = for {
        mode      <- ZIO.fromEither(parsed).orElseFail(RunError.Ignore)
        windowOpt <- WindowManager.targetWindow
        _         <- ZIO.foreachDiscard(windowOpt)(BorderlessCommand.run(_, mode, originalStylesRef))
      } yield ()

      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.veryHigh(input.context))
          .rendered(Renderer.renderDefault(title, message))
      )
    }
}

object BorderlessCommand extends CommandPlugin[BorderlessCommand] {

  sealed trait Mode {
    def description: String
  }

  object Mode {

    case object Fill extends Mode {
      val description = "Remove title bar and fill the screen"
    }

    case object Strip extends Mode {
      val description = "Remove title bar only"
    }

    case object Restore extends Mode {
      val description = "Restore title bar and original size"
    }

    def fromString(s: String): Option[Mode] = s.toLowerCase match {
      case "fill" | "full" => Some(Fill)
      case "strip"         => Some(Strip)
      case "restore"       => Some(Restore)
      case _               => None
    }
  }

  def make(config: Config): ZIO[Env, CommandPluginError, BorderlessCommand] =
    for {
      commandNames    <- config.getZIO[Option[List[String]]]("commandNames")
      toggleShortcuts <- config.getZIO[Option[List[KeyboardShortcut]]]("toggleShortcuts")
      toggleModeName  <- config.getZIO[Option[String]]("toggleMode")
      toggleMode      <- ZIO
                      .foreach(toggleModeName) { name =>
                        ZIO
                          .fromOption(Mode.fromString(name).filter(_ != Mode.Restore))
                          .orElseFail(
                            CommandPluginError.UnexpectedException(
                              new IllegalArgumentException(s"Invalid toggleMode `$name`. Expected: fill, strip")
                            )
                          )
                      }
                      .map(_.getOrElse(Mode.Fill))
      originalStylesRef <- Ref.make(Map.empty[Long, WindowStyles])
      _                 <- ZIO
             .foreachDiscard(toggleShortcuts.getOrElse(List.empty)) { shortcut =>
               Shortcuts.addGlobalShortcut(shortcut)(_ =>
                 (for {
                   windowOpt <- WindowManager.targetWindow
                   _         <- ZIO.foreachDiscard(windowOpt)(toggle(_, toggleMode, originalStylesRef))
                 } yield ()).tapErrorCause { t =>
                   ZIO.logWarningCause("Error running Borderless command", t)
                 }.ignore
               )
             }
             .when(OS.os == OS.Windows)
             .mapError(CommandPluginError.UnexpectedException.apply)
    } yield BorderlessCommand(commandNames.getOrElse(List("borderless")), originalStylesRef)

  /** Restores the window if this command made it borderless, otherwise applies
    * `mode`.
    */
  def toggle(window: HWND, mode: Mode, originalStylesRef: Ref[Map[Long, WindowStyles]]): Task[Unit] =
    for {
      isBorderless <- originalStylesRef.get.map(_.contains(Pointer.nativeValue(window.getPointer)))
      _            <- run(window, if (isBorderless) Mode.Restore else mode, originalStylesRef)
    } yield ()

  def run(
      window: HWND,
      mode: Mode,
      originalStylesRef: Ref[Map[Long, WindowStyles]]
  ): Task[Unit] = {
    val key = Pointer.nativeValue(window.getPointer)

    mode match {
      case Mode.Restore =>
        for {
          stylesOpt <- originalStylesRef.modify(m => (m.get(key), m - key))
          _         <- ZIO.foreachDiscard(stylesOpt)(WindowManager.restoreWindowStyles(window, _))
        } yield ()

      case Mode.Fill | Mode.Strip =>
        for {
          styles <- ZIO.attempt(WindowManager.windowStyles(window))
          // Keep the first captured styles so applying twice doesn't overwrite the original with the borderless ones
          _ <- originalStylesRef.update(m => if (m.contains(key)) m else m.updated(key, styles))
          _ <- WindowManager.makeBorderless(window, fillMonitor = mode == Mode.Fill)
        } yield ()
    }
  }
}

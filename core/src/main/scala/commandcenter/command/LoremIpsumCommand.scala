package commandcenter.command

import cats.data.Validated
import cats.syntax.apply.*
import com.monovore.decline
import com.monovore.decline.Opts
import com.typesafe.config.Config
import commandcenter.command.LoremIpsumCommand.ChunkType
import commandcenter.tools.Tools
import commandcenter.view.Renderer
import commandcenter.CCRuntime.Env
import fansi.Str
import zio.*

import scala.io.Source

final case class LoremIpsumCommand(commandNames: List[String], lipsum: String) extends Command[Unit] {
  val commandType: CommandType = CommandType.LoremIpsumCommand
  val title: String = "Lorem Ipsum"

  val numOpt = Opts.argument[Int]("number").withDefault(1)

  val typeOpt = Opts
    .argument[String]("words, sentences, or paragraphs")
    .mapValidated {
      case arg if arg.matches("words?")      => Validated.valid(ChunkType.Word)
      case arg if arg.matches("sentences?")  => Validated.valid(ChunkType.Sentence)
      case arg if arg.matches("paragraphs?") => Validated.valid(ChunkType.Paragraph)
      case s                                 => Validated.invalidNel(s"$s is not valid: should be 'words', 'sentences', or 'paragraphs'.")
    }
    .withDefault(ChunkType.Paragraph)

  val lipsumCommand = decline.Command(title, title)((numOpt, typeOpt).tupled)

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asArgs).orElseFail(CommandError.NotApplicable)
      parsed = lipsumCommand.parse(input.args)
      message <- ZIO
                   .fromEither(parsed)
                   .fold(
                     HelpMessage.formatted,
                     { case (i, chunkType) =>
                       Str(s"Will generate $i ${chunkType.toString}s to the clipboard")
                     }
                   )
    } yield {
      val run = for {
        (i, chunkType) <- ZIO.fromEither(parsed).mapError(RunError.CliError.apply)
        text = chunkType match {
                 case ChunkType.Word => Iterator.continually(lipsum.split("\\s")).flatten.take(i).mkString(" ")
                 case ChunkType.Sentence =>
                   Iterator.continually(lipsum.split("\\.")).flatten.take(i).mkString(". ") ++ "."
                 case ChunkType.Paragraph => Iterator.continually(lipsum).take(i).mkString("\n")
               }
        _ <- Tools.setClipboard(text)
      } yield ()
      PreviewResults.one(
        Preview.unit
          .onRun(run)
          .score(Scores.veryHigh(input.context))
          .rendered(Renderer.renderDefault("Lorem Ipsum", message))
      )
    }
}

object LoremIpsumCommand extends CommandPlugin[LoremIpsumCommand] {
  sealed trait ChunkType

  object ChunkType {
    case object Word extends ChunkType
    case object Sentence extends ChunkType
    case object Paragraph extends ChunkType
  }

  def make(config: Config): IO[CommandPluginError, LoremIpsumCommand] =
    for {
      commandNames <- config.getZIO[Option[List[String]]]("commandNames")
      lipsum <- ZIO.scoped {
                  ZIO
                    .fromAutoCloseable(ZIO.attempt(Source.fromResource("lipsum")))
                    .mapAttempt(_.getLines().mkString("\n"))
                    .mapError(CommandPluginError.UnexpectedException.apply)
                }
    } yield LoremIpsumCommand(commandNames.getOrElse(List("lipsum", "lorem", "ipsum")), lipsum)

}

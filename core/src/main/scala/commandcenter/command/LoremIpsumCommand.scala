package commandcenter.command

import cats.data.Validated
import cats.syntax.apply._
import com.monovore.decline
import com.monovore.decline.Opts
import commandcenter.CommandContext
import commandcenter.util.ProcessUtil
import commandcenter.view.DefaultView
import io.circe.Decoder
import scala.io.Source
import zio._

sealed trait ChunkType

case object Word extends ChunkType

case object Sentence extends ChunkType

case object Paragraph extends ChunkType

final case class LoremIpsumCommand() extends Command[Unit] {
  val commandType: CommandType = CommandType.LoremIpsumCommand

  val commandNames: List[String] = List("lipsum", "lorem", "ipsum")

  val title: String = "Lorem Ipsum"

  val lipsum = Source.fromResource("lipsum").getLines().mkString("\n")

  val numOpt = Opts.argument[Int]("number").withDefault(1)
  val typeOpt = Opts.argument[String]("word, sentence, or paragraph").mapValidated {
    case "words" => Validated.valid(Word)
    case "sentences" => Validated.valid(Sentence)
    case "paragraphs" => Validated.valid(Paragraph)
    case s => Validated.invalidNel(s"${s} is not valid: should be 'words', 'sentences', or 'paragraphs'.")
  }.withDefault(Paragraph)

  val lipsumCommand = decline.Command("Lorem Ipsum", title)((numOpt, typeOpt).tupled)

  override def argsPreview(args: List[String], context: CommandContext): IO[CommandError, List[PreviewResult[Unit]]] = {
    val parsed = lipsumCommand.parse(args)

    for {
      message <- ZIO.fromEither(parsed)
        .foldM(
          help => UIO(HelpMessage.formatted(help)),
          {
            case (i, chunkType) => UIO(fansi.Str(s"Will generate ${i} ${chunkType.toString}s to the clipboard"))
          }
        )
    } yield {
      val run = for {
        (i, chunkType) <- ZIO.fromEither(parsed)
        text = chunkType match {
          case Word => Iterator.continually(lipsum.split("\\s")).flatten.take(i).mkString(" ")
          case Sentence => Iterator.continually(lipsum.split("\\.")).flatten.take(i).mkString(". ") ++ "."
          case Paragraph => Iterator.continually(lipsum).take(i).mkString("\n")
        }
        _ <- ProcessUtil.copyToClipboard(text)
      } yield ()
      List(Preview.unit
        .onRun(run.ignore)
        .score(Scores.high(context))
        .view(DefaultView("Lorem Ipsum", message))
      )
    }
  }
}

object LoremIpsumCommand extends CommandPlugin[LoremIpsumCommand] {
  implicit val decoder: Decoder[LoremIpsumCommand] = Decoder.const(LoremIpsumCommand())
}
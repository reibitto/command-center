package commandcenter.cli.message

import commandcenter.codec.Codecs
import io.circe.*

sealed trait CCRequest {
  def correlationId: Option[String]
}

object CCRequest {
  final case class Search(term: String, correlationId: Option[String]) extends CCRequest

  object Search {
    implicit val decoder: Decoder[Search] = Decoder.forProduct2("term", "correlationId")(Search.apply)
    implicit val encoder: Encoder[Search] = Encoder.forProduct2("term", "correlationId")(s => (s.term, s.correlationId))
  }

  final case class Run(index: Int, correlationId: Option[String]) extends CCRequest

  object Run {
    implicit val decoder: Decoder[Run] = Decoder.forProduct2("index", "correlationId")(Run.apply)
    implicit val encoder: Encoder[Run] = Encoder.forProduct2("index", "correlationId")(s => (s.index, s.correlationId))
  }

  final case class Exit(correlationId: Option[String]) extends CCRequest

  object Exit {
    implicit val decoder: Decoder[Exit] = Decoder.forProduct1("correlationId")(Exit.apply)
    implicit val encoder: Encoder[Exit] = Encoder.forProduct1("correlationId")(s => s.correlationId)
  }

  implicit val decoder: Decoder[CCRequest] = Codecs.decodeSumBySoleKey {
    case ("search", c) => c.as[Search]
    case ("run", c)    => c.as[Run]
    case ("exit", c)   => c.as[Exit]
  }

  implicit val encoder: Encoder[CCRequest] = Encoder.instance {
    case a: Search => Search.encoder(a)
    case a: Run    => Run.encoder(a)
    case a: Exit   => Exit.encoder(a)
  }
}

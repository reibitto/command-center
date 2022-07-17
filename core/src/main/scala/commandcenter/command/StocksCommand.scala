package commandcenter.command

import com.typesafe.config.Config
import commandcenter.command.StocksCommand.{StocksResult, Ticker}
import commandcenter.CCRuntime.Env
import commandcenter.HttpClient
import io.circe.{Decoder, Json}
import sttp.client3.{UriContext, basicRequest}
import sttp.client3.circe.asJson
import zio.managed.*
import zio.ZIO

final case class StocksCommand(commandNames: List[String], tickers: List[Ticker]) extends Command[Unit] {
  val commandType: CommandType = CommandType.StocksCommand
  val title: String = "Stock"
  val stocksUrl: String = "https://query1.finance.yahoo.com/v7/finance/quote?symbols="

  def preview(searchInput: SearchInput): ZIO[Env, CommandError, PreviewResults[Unit]] =
    for {
      input <- ZIO.fromOption(searchInput.asPrefixed).orElseFail(CommandError.NotApplicable)
      symbols = if (input.rest.isBlank) tickers.map(_.id).mkString(",") else input.rest
      request = basicRequest
                  .get(uri"$stocksUrl$symbols")
                  .response(asJson[Json])
      response <- request.send(HttpClient.backend)
                    .map(_.body)
                    .absolve
                    .mapError(CommandError.UnexpectedException)
      stocks <- ZIO
                  .fromEither(
                    response.hcursor.downField("quoteResponse").downField("result").as[List[StocksResult]]
                  )
                  .mapError(CommandError.UnexpectedException)
    } yield PreviewResults.fromIterable(stocks.map { stock =>
      Preview.unit
        .score(Scores.high(input.context))
        .renderedAnsi(
          fansi.Str(
            fansi.Color.Cyan(stock.ticker),
            fansi.Color.LightCyan(s" (${stock.name}) "),
            String.format("%.2f", stock.price),
            s" ${stock.currency}",
            getChangePercentField(stock.change, stock.changePercent)
          )
        )
    })

  private def getChangePercentField(change: Double, changePercent: Double): fansi.Str = {
    val changeTextField = s"${String.format("%.2f", change)}"
    val changePercentTextField = s"(${String.format("%.2f", changePercent.abs)}%)"
    if (changePercent >= 0) fansi.Color.Green(s" +$changeTextField $changePercentTextField")
    else fansi.Color.Red(s" $changeTextField $changePercentTextField")
  }
}

object StocksCommand extends CommandPlugin[StocksCommand] {

  final case class StocksResult(
    ticker: String,
    name: String,
    price: Double,
    currency: String,
    change: Double,
    changePercent: Double
  )

  final case class Ticker(id: String)

  object Ticker {
    implicit val decoder: Decoder[Ticker] = Decoder.forProduct1("ticker")(Ticker.apply)
  }

  implicit val decoder: Decoder[StocksResult] =
    Decoder.forProduct6(
      "symbol",
      "shortName",
      "regularMarketPrice",
      "currency",
      "regularMarketChange",
      "regularMarketChangePercent"
    )(StocksResult.apply)

  def make(config: Config): Managed[CommandPluginError, StocksCommand] =
    for {
      commandNames <- config.getManaged[Option[List[String]]]("commandNames")
      tickers      <- config.getManaged[List[Ticker]]("tickers")
    } yield StocksCommand(commandNames.getOrElse(List("stock", "stocks")), tickers)
}

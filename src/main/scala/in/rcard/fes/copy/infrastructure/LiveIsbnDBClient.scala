package in.rcard.fes.copy.infrastructure

import in.rcard.fes.AppConfig.IsbnClientConfig
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.yaes.Sync
import in.rcard.yaes.http.circe.given
import in.rcard.yaes.http.client.ConnectionError
import in.rcard.yaes.http.client.HttpError
import in.rcard.yaes.http.client.HttpRequest
import in.rcard.yaes.http.client.UriParam.given
import scala.language.implicitConversions
import in.rcard.yaes.http.client.YaesClient
import in.rcard.yaes.http.client.as
import in.rcard.yaes.http.core.DecodingError
import in.rcard.yaes.http.core.Headers
import in.rcard.yaes.raises

class LiveIsbnDBClient(httpClient: YaesClient, clientConfig: IsbnClientConfig)
    extends IsbnDBClient {

  override def find(
      isbn: ISBN
  )(using Sync): IsbnDBClient.BookDto raises (ConnectionError | HttpError | DecodingError) = {
    import IsbnDBClient.BookDto
    val req = HttpRequest
      .get(clientConfig.host / "books" / isbn.value)
      .header(Headers.Authorization, clientConfig.apiKey)
      .queryParam("with_prices", "false")
    val res = httpClient.send(req)
    res.as[BookDto]
  }
}

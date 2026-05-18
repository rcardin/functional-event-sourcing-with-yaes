package in.rcard.fes.copy.infrastructure

import scala.language.implicitConversions
import in.rcard.fes.AppConfig.IsbnClientConfig
import in.rcard.fes.copy.domain.Domain.Author
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.fes.copy.domain.Domain.Title
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.Error
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.CopyToRegister
import in.rcard.fes.util.UriOps.*
import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.http.circe.given
import in.rcard.yaes.http.client.ClientHttpError
import in.rcard.yaes.http.client.ConnectionError
import in.rcard.yaes.http.client.HttpError
import in.rcard.yaes.http.client.HttpRequest
import in.rcard.yaes.http.client.HttpResponse
import in.rcard.yaes.http.client.Uri
import in.rcard.yaes.http.client.UriParam.given
import in.rcard.yaes.http.client.YaesClient
import in.rcard.yaes.http.client.as
import in.rcard.yaes.http.core.Headers
import in.rcard.yaes.raises
import io.circe.Decoder
import in.rcard.yaes.Log

object FindCopyByIsbnRepository {

  def apply(
      httpClient: YaesClient,
      clientConfig: IsbnClientConfig
  )(using Log): FindCopyByIsbnPort = {

    case class MerchantLogoOffsetDto(x: String, y: String) derives Decoder

    case class PriceDto(
        condition: String,
        merchant: String,
        merchantLogo: String,
        merchantLogoOffset: MerchantLogoOffsetDto,
        shipping: String,
        price: String,
        total: String,
        link: String
    ) derives Decoder

    case class OtherIsbnDto(isbn: String, binding: String) derives Decoder

    case class BookDto(
        title: String,
        isbn13: String,
        isbn10: String,
        deweyDecimal: List[String],
        binding: String,
        publisher: String,
        language: String,
        datePublished: String,
        edition: String,
        pages: Int,
        dimensionsStructured: List[String],
        image: String,
        imageOriginal: String,
        msrp: Double,
        excerpt: String,
        synopsis: String,
        authors: List[String],
        subjects: List[String],
        prices: List[PriceDto],
        otherIsbns: List[OtherIsbnDto]
    ) derives Decoder

    new FindCopyByIsbnPort {

      val logger = Log.getLogger("FindCopyByIsbnPort")

      override def find(isbn: ISBN)(using Sync): CopyToRegister raises Error = {

        val req = HttpRequest
          .get(clientConfig.host / "books" / isbn.value)
          .header(Headers.Authorization, clientConfig.apiKey)
          .queryParam("with_prices", "false")

        Raise.recover {
          val res     = httpClient.send(req)
          val bookDto = res.as[BookDto]
          CopyToRegister(
            isbn = isbn,
            title = Title(bookDto.title),
            authors = bookDto.authors.map(Author(_))
          )

        } {
          case ce: ConnectionError =>
            logger.error(s"Connection error: $ce")
            Raise.raise(FindCopyByIsbnPort.Error.UnexpectedError(s"Connection error"))
          case _: HttpError.NotFound =>
            logger.warn(s"ISBN not found: ${isbn.value}")
            Raise.raise(FindCopyByIsbnPort.Error.NotFound(isbn))
          // FIXME The HttpError should have a common way to retrive the body
          case he: HttpError =>
            logger.error(s"Unexpected HTTP error: ${he.body}")
            Raise.raise(FindCopyByIsbnPort.Error.UnexpectedError(s"Unexpected HTTP error"))
          case in.rcard.yaes.http.core.DecodingError.ParseError(msg, _) =>
            // FIXME An error version with the exception
            logger.error(s"Error parsing response from ISBN service: $msg")
            Raise.raise(
              FindCopyByIsbnPort.Error.UnexpectedError(s"Error parsing response from ISBN service")
            )
          case in.rcard.yaes.http.core.DecodingError.ValidationErrors(errors) =>
            logger.error(
              s"Validation error while parsing response from ISBN service: ${errors.toList.mkString}"
            )
            Raise.raise(
              FindCopyByIsbnPort.Error.UnexpectedError(
                s"Validation error while parsing response from ISBN service"
              )
            )
        }
      }
    }
  }

  given live(using l: Log, client: YaesClient, isbnClientConfig: IsbnClientConfig): FindCopyByIsbnPort =
    FindCopyByIsbnRepository(client, isbnClientConfig)

}

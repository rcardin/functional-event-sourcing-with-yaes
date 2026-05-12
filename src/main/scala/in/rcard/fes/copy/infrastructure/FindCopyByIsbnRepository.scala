package in.rcard.fes.copy.infrastructure

import in.rcard.fes.AppConfig.IsbnClientConfig
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase.CopyToRegister
import in.rcard.yaes.Raise
import in.rcard.yaes.Reader
import in.rcard.yaes.Reader.read
import in.rcard.yaes.Reader.reader
import in.rcard.yaes.Sync
import in.rcard.yaes.http.client.HttpRequest
import in.rcard.yaes.http.client.HttpResponse
import in.rcard.yaes.http.client.as
import in.rcard.yaes.http.client.Uri
import in.rcard.yaes.http.client.YaesClient
import in.rcard.yaes.raises
import in.rcard.yaes.reads
import in.rcard.yaes.http.client.UriParam.given
import in.rcard.fes.util.UriOps.*
import in.rcard.yaes.http.core.Headers

object FindCopyByIsbnRepository {

  def apply(
      httpClient: YaesClient,
      clientConfig: IsbnClientConfig
  )(using Sync): FindCopyByIsbnPort = {

    case class MerchantLogoOffsetDto(x: String, y: String)

    case class PriceDto(
        condition: String,
        merchant: String,
        merchantLogo: String,
        merchantLogoOffset: MerchantLogoOffsetDto,
        shipping: String,
        price: String,
        total: String,
        link: String
    )

    case class OtherIsbnDto(isbn: String, binding: String)

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
    )

    new FindCopyByIsbnPort {
      override def find(isbn: ISBN): CopyToRegister raises FindCopyByIsbnPort.Error = {

        val req = HttpRequest
          .get(clientConfig.host / "books" / isbn.value)
          .header(Headers.Authorization, clientConfig.apiKey)
          .queryParam("with_prices", "false")

        Raise.recover {
          val res = httpClient.send(req)
          res.status match {
            case 200 =>
              val bookDto = res.as[BookDto]

          }

        } { connError =>
          Raise.raise(FindCopyByIsbnPort.Error.UnexpectedError(s"Connection error"))
        }
      }
    }
  }

  given live(using Sync, Reader[YaesClient], Reader[IsbnClientConfig]): Reader[FindCopyByIsbnPort] =
    reader(
      FindCopyByIsbnRepository(read[YaesClient], read[IsbnClientConfig])
    )

}

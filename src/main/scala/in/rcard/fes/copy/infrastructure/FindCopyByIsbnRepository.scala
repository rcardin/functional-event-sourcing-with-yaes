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
import in.rcard.yaes.http.client.Uri
import in.rcard.yaes.http.client.YaesClient
import in.rcard.yaes.raises
import in.rcard.yaes.reads

object FindCopyByIsbnRepository {

  // FIXME: Probably it's better to move the check of URI validity out of here
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
          .get(clientConfig.host)
          .header("Authorization", s"Bearer ${clientConfig.apiKey}")
          .queryParam("isbn", isbn.value)
        // httpClient.send(req)
        null
      }
    }
  }

  given live(using Sync, Reader[YaesClient], Reader[IsbnClientConfig]): Reader[FindCopyByIsbnPort] =
    reader(
      FindCopyByIsbnRepository(read[YaesClient], read[IsbnClientConfig])
    )

}

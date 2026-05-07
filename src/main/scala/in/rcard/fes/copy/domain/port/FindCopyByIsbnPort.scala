package in.rcard.fes.copy.domain.port

import in.rcard.fes.copy.domain.Domain
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.Error
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase.CopyToRegister
import in.rcard.fes.utils.reader
import in.rcard.yaes.Reader.read
import in.rcard.yaes.http.client.{HttpRequest, Uri, YaesClient}
import in.rcard.yaes.{Reader, raises, reads}

trait FindCopyByIsbnPort {
  // FIXME Move the CopuToRegister here
  def find(isbn: ISBN): CopyToRegister raises Error
}
object FindCopyByIsbnPort {
  
  case class IsbnClientConfig(
      baseUrl: Uri,
      apiKey: String
  )
  
  def apply(httpClient: YaesClient, clientConfig: IsbnClientConfig): FindCopyByIsbnPort = new FindCopyByIsbnPort {
    override def find(isbn: ISBN): CopyToRegister raises Error = ??? 
//    {
//      // httpClient.send(HttpRequest.get(Uri(clientConfig.))
//    }
  }

  given live: Reader[FindCopyByIsbnPort] reads YaesClient reads IsbnClientConfig = reader(
    FindCopyByIsbnPort(read[YaesClient], read[IsbnClientConfig])
  )

  enum Error {
    case NotFound(isbn: ISBN)
    case UnexpectedError(message: String)
  }

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
}

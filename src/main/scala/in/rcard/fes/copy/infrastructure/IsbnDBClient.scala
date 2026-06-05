package in.rcard.fes.copy.infrastructure

import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.yaes.Sync
import in.rcard.yaes.http.client.ConnectionError
import in.rcard.yaes.http.client.HttpError
import in.rcard.yaes.http.core.DecodingError
import in.rcard.yaes.raises
import io.circe.Decoder

trait IsbnDBClient {
  def find(isbn: ISBN)(using
      Sync
  ): IsbnDBClient.BookDto raises (ConnectionError | HttpError | DecodingError)
}

object IsbnDBClient {

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
}

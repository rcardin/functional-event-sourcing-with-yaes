package in.rcard.fes.copy.infrastructure

import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.yaes.Async
import in.rcard.yaes.Raise
import in.rcard.yaes.Retry
import in.rcard.yaes.Schedule
import in.rcard.yaes.Sync
import in.rcard.yaes.http.client.ConnectionError
import in.rcard.yaes.http.client.HttpError
import in.rcard.yaes.http.core.DecodingError
import in.rcard.yaes.raises

class RetryIsbnDBClient(inner: IsbnDBClient, schedule: Schedule) extends IsbnDBClient {

  override def find(isbn: ISBN)(using Sync): IsbnDBClient.BookDto raises (ConnectionError | HttpError | DecodingError) =
    Async.run {
      Retry[ConnectionError](schedule) {
        Raise.either[ConnectionError | HttpError | DecodingError, IsbnDBClient.BookDto] {
          inner.find(isbn)
        } match {
          case Right(dto)                => dto
          case Left(ce: ConnectionError) => Raise.raise(ce)
          case Left(other)               => Raise.raise(other)
        }
      }
    }
}

package in.rcard.fes.copy.infrastructure

import in.rcard.fes.AppConfig.IsbnClientConfig
import in.rcard.fes.copy.domain.Domain.Author
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.fes.copy.domain.Domain.Title
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.CopyToRegister
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.Error
import in.rcard.yaes.Log
import in.rcard.yaes.Raise
import in.rcard.yaes.Schedule
import in.rcard.yaes.Sync
import in.rcard.yaes.http.client.ConnectionError
import in.rcard.yaes.http.client.YaesClient
import in.rcard.yaes.http.core.DecodingError
import in.rcard.yaes.raises

import scala.concurrent.duration.*

object FindCopyByIsbnRepository {

  def apply(client: IsbnDBClient)(using Log): FindCopyByIsbnPort =
    new FindCopyByIsbnPort {

      val logger = Log.getLogger("FindCopyByIsbnPort")

      override def find(isbn: ISBN)(using Sync): CopyToRegister raises Error = {
        import in.rcard.yaes.http.client.HttpError
        import in.rcard.yaes.http.core.DecodingError

        Raise.recover {
          val bookDto = client.find(isbn)
          CopyToRegister(
            isbn    = isbn,
            title   = Title(bookDto.title),
            authors = bookDto.authors.map(Author(_))
          )
        } {
          case ce: ConnectionError =>
            logger.error(s"Connection error: $ce")
            Raise.raise(FindCopyByIsbnPort.Error.UnexpectedError(s"Connection error"))
          case _: HttpError.NotFound =>
            logger.warn(s"ISBN not found: ${isbn.value}")
            Raise.raise(FindCopyByIsbnPort.Error.NotFound(isbn))
          case he: HttpError =>
            logger.error(s"Unexpected HTTP error: ${he.body}")
            Raise.raise(FindCopyByIsbnPort.Error.UnexpectedError(s"Unexpected HTTP error"))
          case DecodingError.ParseError(msg, _) =>
            logger.error(s"Error parsing response from ISBN service: $msg")
            Raise.raise(
              FindCopyByIsbnPort.Error.UnexpectedError(s"Error parsing response from ISBN service")
            )
          case DecodingError.ValidationErrors(errors) =>
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

  given live(using l: Log, yaesClient: YaesClient, isbnClientConfig: IsbnClientConfig): FindCopyByIsbnPort =
    val liveClient  = LiveIsbnDBClient(yaesClient, isbnClientConfig)
    val retryClient = RetryIsbnDBClient(liveClient, Schedule.exponential(100.millis, factor = 2.0, max = 2.seconds).attempts(4))
    FindCopyByIsbnRepository(retryClient)
}

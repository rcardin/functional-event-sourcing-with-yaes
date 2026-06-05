package in.rcard.fes.copy.infrastructure

import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.yaes.Raise
import in.rcard.yaes.Schedule
import in.rcard.yaes.Sync
import in.rcard.yaes.http.client.ConnectionError
import in.rcard.yaes.http.client.HttpError
import in.rcard.yaes.http.core.DecodingError
import in.rcard.yaes.raises
import in.rcard.yaes.test.scalatest.RaiseSpec
import in.rcard.yaes.test.scalatest.SyncSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*

class RetryIsbnDBClientSpec extends AnyFlatSpec with SyncSpec with RaiseSpec with Matchers {

  private type FindError = ConnectionError | HttpError | DecodingError

  private val ce      = ConnectionError.ConnectionRefused("host", 8080)
  private val he      = HttpError.InternalServerError("boom")
  private val noDelay = Schedule.fixed(Duration.Zero).attempts(3)

  private def minimalDto(title: String): IsbnDBClient.BookDto =
    IsbnDBClient.BookDto(
      title                = title,
      isbn13               = "",
      isbn10               = "",
      deweyDecimal         = Nil,
      binding              = "",
      publisher            = "",
      language             = "",
      datePublished        = "",
      edition              = "",
      pages                = 0,
      dimensionsStructured = Nil,
      image                = "",
      imageOriginal        = "",
      msrp                 = 0.0,
      excerpt              = "",
      synopsis             = "",
      authors              = Nil,
      subjects             = Nil,
      prices               = Nil,
      otherIsbns           = Nil
    )

  "RetryIsbnDBClient" should "retry on ConnectionError and succeed on eventual success" in withSync {
    val callCount = AtomicInteger(0)
    val stub = new IsbnDBClient {
      override def find(isbn: ISBN)(using Sync): IsbnDBClient.BookDto raises FindError =
        if callCount.incrementAndGet() < 3 then Raise.raise(ce) else minimalDto("Foundation")
    }
    val client = RetryIsbnDBClient(stub, noDelay)
    val result = failOnRaise[FindError, IsbnDBClient.BookDto](client.find(FOUNDATION_ISBN))
    result.title shouldBe "Foundation"
    callCount.get() shouldBe 3
  }

  it should "NOT retry on HttpError" in withSync {
    val callCount = AtomicInteger(0)
    val stub = new IsbnDBClient {
      override def find(isbn: ISBN)(using Sync): IsbnDBClient.BookDto raises FindError = {
        callCount.incrementAndGet()
        Raise.raise(he)
      }
    }
    val client = RetryIsbnDBClient(stub, noDelay)
    val error = interceptRaised[FindError, IsbnDBClient.BookDto](client.find(FOUNDATION_ISBN))
    error shouldBe he
    callCount.get() shouldBe 1
  }

  it should "raise ConnectionError after all retries exhausted" in withSync {
    val stub = new IsbnDBClient {
      override def find(isbn: ISBN)(using Sync): IsbnDBClient.BookDto raises FindError =
        Raise.raise(ce)
    }
    val client = RetryIsbnDBClient(stub, noDelay)
    val error = interceptRaised[FindError, IsbnDBClient.BookDto](client.find(FOUNDATION_ISBN))
    error shouldBe ce
  }
}

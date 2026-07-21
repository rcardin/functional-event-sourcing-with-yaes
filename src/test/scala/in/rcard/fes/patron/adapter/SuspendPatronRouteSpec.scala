package in.rcard.fes.patron.adapter

import in.rcard.fes.patron.Fixtures.*
import in.rcard.fes.patron.application.{SuspendPatronError, SuspendPatronUseCase}
import in.rcard.fes.patron.domain.Domain.*
import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.http.core.Method.POST
import in.rcard.yaes.http.server.Request
import in.rcard.yaes.http.server.Routes as YaesRoutes
import in.rcard.yaes.raises
import in.rcard.yaes.test.scalatest.SyncSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private val SUSPEND_PATRON_NOT_FOUND_RESPONSE_JSON =
  s"""{"title":"Not Found","detail":"The patron was not found.","errors":[{"detail":"The patron with card id '$NOT_REGISTERED_CARD_ID_VALUE' was not found."}]}"""

private val SUSPEND_PATRON_ALREADY_SUSPENDED_RESPONSE_JSON =
  s"""{"title":"Conflict","detail":"Patron already suspended.","errors":[{"detail":"The patron with card id '$ALREADY_SUSPENDED_CARD_ID_VALUE' is already suspended."}]}"""

private val SUSPEND_PATRON_UNEXPECTED_ERROR_RESPONSE_JSON =
  """{"title":"Unexpected error","detail":"An unexpected error occurred.","errors":[{"detail":"Unexpected error"}]}"""

class SuspendPatronRouteSpec extends AnyFlatSpec with SyncSpec with Matchers {

  private val mockedSuspendPatronUseCase = new SuspendPatronUseCase {
    override def suspend(cardId: PatronId)(using Sync): Unit raises SuspendPatronError =
      cardId match {
        case NOT_REGISTERED_CARD_ID =>
          Raise.raise(SuspendPatronError.PatronNotFound(NOT_REGISTERED_CARD_ID))
        case ALREADY_SUSPENDED_CARD_ID =>
          Raise.raise(SuspendPatronError.AlreadySuspended(ALREADY_SUSPENDED_CARD_ID))
        case UNEXPECTED_CARD_ID =>
          Raise.raise(SuspendPatronError.UnexpectedError("Unexpected error"))
        case CARD_ID =>
          ()
      }
  }

  private val underTest: Sync ?=> YaesRoutes = YaesRoutes(
    SuspendPatronRoute(mockedSuspendPatronUseCase).suspendPatronRoute
  )

  "SuspendPatronRoute" should "return 200 if the patron is suspended successfully" in withSync {

    val request = Request(POST, s"/patrons/$CARD_ID_VALUE/suspended", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 200
    actualResponse.body shouldBe "\"Ok\""
  }

  it should "return 404 if the patron was not found" in withSync {

    val request = Request(POST, s"/patrons/$NOT_REGISTERED_CARD_ID_VALUE/suspended", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 404
    actualResponse.body shouldBe SUSPEND_PATRON_NOT_FOUND_RESPONSE_JSON
  }

  it should "return 409 if the patron is already suspended" in withSync {

    val request = Request(POST, s"/patrons/$ALREADY_SUSPENDED_CARD_ID_VALUE/suspended", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
    actualResponse.body shouldBe SUSPEND_PATRON_ALREADY_SUSPENDED_RESPONSE_JSON
  }

  it should "return 500 if the use case raises an unexpected error" in withSync {

    val request = Request(POST, s"/patrons/$UNEXPECTED_CARD_ID_VALUE/suspended", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 500
    actualResponse.body shouldBe SUSPEND_PATRON_UNEXPECTED_ERROR_RESPONSE_JSON
  }
}

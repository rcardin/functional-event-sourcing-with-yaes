package in.rcard.fes.patron.adapter

import in.rcard.fes.patron.Fixtures.*
import in.rcard.fes.patron.application.{ReinstatePatronError, ReinstatePatronUseCase}
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

private val REINSTATE_PATRON_NOT_FOUND_RESPONSE_JSON =
  s"""{"title":"Not Found","detail":"The patron was not found.","errors":[{"detail":"The patron with card id '$NOT_REGISTERED_CARD_ID_VALUE' was not found."}]}"""

private val REINSTATE_PATRON_NOT_SUSPENDED_RESPONSE_JSON =
  s"""{"title":"Conflict","detail":"Patron is not suspended.","errors":[{"detail":"The patron with card id '$CARD_ID_VALUE' is not suspended and cannot be reinstated."}]}"""

private val REINSTATE_PATRON_UNEXPECTED_ERROR_RESPONSE_JSON =
  """{"title":"Unexpected error","detail":"An unexpected error occurred.","errors":[{"detail":"Unexpected error"}]}"""

class ReinstatePatronRouteSpec extends AnyFlatSpec with SyncSpec with Matchers {

  private val mockedReinstatePatronUseCase = new ReinstatePatronUseCase {
    override def reinstate(cardId: PatronId)(using Sync): Unit raises ReinstatePatronError =
      cardId match {
        case NOT_REGISTERED_CARD_ID =>
          Raise.raise(ReinstatePatronError.PatronNotFound(NOT_REGISTERED_CARD_ID))
        case CARD_ID =>
          Raise.raise(ReinstatePatronError.NotSuspended(CARD_ID))
        case UNEXPECTED_CARD_ID =>
          Raise.raise(ReinstatePatronError.UnexpectedError("Unexpected error"))
        case ALREADY_SUSPENDED_CARD_ID =>
          ()
      }
  }

  private val underTest: Sync ?=> YaesRoutes = YaesRoutes(
    ReinstatePatronRoute(mockedReinstatePatronUseCase).reinstatePatronRoute
  )

  "ReinstatePatronRoute" should "return 200 if the patron is reinstated successfully" in withSync {

    val request = Request(POST, s"/patrons/$ALREADY_SUSPENDED_CARD_ID_VALUE/reinstated", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 200
    actualResponse.body shouldBe "\"Ok\""
  }

  it should "return 404 if the patron was not found" in withSync {

    val request = Request(POST, s"/patrons/$NOT_REGISTERED_CARD_ID_VALUE/reinstated", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 404
    actualResponse.body shouldBe REINSTATE_PATRON_NOT_FOUND_RESPONSE_JSON
  }

  it should "return 409 if the patron is not suspended" in withSync {

    val request = Request(POST, s"/patrons/$CARD_ID_VALUE/reinstated", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
    actualResponse.body shouldBe REINSTATE_PATRON_NOT_SUSPENDED_RESPONSE_JSON
  }

  it should "return 500 if the use case raises an unexpected error" in withSync {

    val request = Request(POST, s"/patrons/$UNEXPECTED_CARD_ID_VALUE/reinstated", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 500
    actualResponse.body shouldBe REINSTATE_PATRON_UNEXPECTED_ERROR_RESPONSE_JSON
  }
}

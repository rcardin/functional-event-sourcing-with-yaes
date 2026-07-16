package in.rcard.fes.patron.adapter

import in.rcard.fes.patron.Fixtures.*
import in.rcard.fes.patron.application.{RegisterPatronError, RegisterPatronUseCase}
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

private val REGISTER_PATRON_REQUEST_JSON =
  s"""{"cardId": "$CARD_ID_VALUE", "name": "$PATRON_NAME_VALUE", "borrowLimit": $BORROW_LIMIT_VALUE}"""

private val REGISTER_ALREADY_REGISTERED_PATRON_REQUEST_JSON =
  s"""{"cardId": "$ALREADY_REGISTERED_CARD_ID_VALUE", "name": "$PATRON_NAME_VALUE", "borrowLimit": $BORROW_LIMIT_VALUE}"""

private val REGISTER_PATRON_UNEXPECTED_ERROR_REQUEST_JSON =
  s"""{"cardId": "$UNEXPECTED_CARD_ID_VALUE", "name": "$PATRON_NAME_VALUE", "borrowLimit": $BORROW_LIMIT_VALUE}"""

private val REGISTER_PATRON_EMPTY_CARD_ID_REQUEST_JSON =
  s"""{"cardId": "", "name": "$PATRON_NAME_VALUE", "borrowLimit": $BORROW_LIMIT_VALUE}"""

private val REGISTER_PATRON_INVALID_BORROW_LIMIT_REQUEST_JSON =
  s"""{"cardId": "$CARD_ID_VALUE", "name": "$PATRON_NAME_VALUE", "borrowLimit": 0}"""

private val REGISTER_ALREADY_REGISTERED_PATRON_RESPONSE_JSON =
  s"""{"title":"Conflict","detail":"Patron already registered.","errors":[{"detail":"The patron with card id '$ALREADY_REGISTERED_CARD_ID_VALUE' is already registered."}]}"""

private val REGISTER_PATRON_UNEXPECTED_ERROR_RESPONSE_JSON =
  """{"title":"Unexpected error","detail":"An unexpected error occurred.","errors":[{"detail":"Unexpected error"}]}"""

class RegisterPatronRouteSpec extends AnyFlatSpec with SyncSpec with Matchers {

  private val mockedRegisterPatronUseCase = new RegisterPatronUseCase {
    override def registerPatron(cardId: PatronId, name: PatronName, borrowLimit: BorrowLimit)(using
        Sync
    ): PatronId raises RegisterPatronError =
      cardId match {
        case ALREADY_REGISTERED_CARD_ID =>
          Raise.raise(RegisterPatronError.AlreadyRegistered(ALREADY_REGISTERED_CARD_ID))
        case UNEXPECTED_CARD_ID =>
          Raise.raise(RegisterPatronError.UnexpectedError("Unexpected error"))
        case CARD_ID =>
          CARD_ID
      }
  }

  private val underTest: Sync ?=> YaesRoutes = YaesRoutes(
    RegisterPatronRoute(mockedRegisterPatronUseCase).registerPatronRoute
  )

  "RegisterPatronRoute" should "return 201 with a Location header if the patron is registered successfully" in withSync {

    val request = Request(POST, "/patrons", Map.empty, REGISTER_PATRON_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 201
    actualResponse.headers("location") shouldBe s"/patrons/$CARD_ID_VALUE"
  }

  it should "return 400 if the DTO is not valid" in withSync {

    val request = Request(POST, "/patrons", Map.empty, "not_valid", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 400
  }

  it should "return 400 if the card id is empty" in withSync {

    val request =
      Request(POST, "/patrons", Map.empty, REGISTER_PATRON_EMPTY_CARD_ID_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 400
  }

  it should "return 400 if the borrow limit is out of range" in withSync {

    val request =
      Request(POST, "/patrons", Map.empty, REGISTER_PATRON_INVALID_BORROW_LIMIT_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 400
  }

  it should "return 409 if the patron is already registered" in withSync {

    val request =
      Request(POST, "/patrons", Map.empty, REGISTER_ALREADY_REGISTERED_PATRON_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
    actualResponse.body shouldBe REGISTER_ALREADY_REGISTERED_PATRON_RESPONSE_JSON
  }

  it should "return 500 if the use case raises an unexpected error" in withSync {

    val request =
      Request(POST, "/patrons", Map.empty, REGISTER_PATRON_UNEXPECTED_ERROR_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 500
    actualResponse.body shouldBe REGISTER_PATRON_UNEXPECTED_ERROR_RESPONSE_JSON
  }
}

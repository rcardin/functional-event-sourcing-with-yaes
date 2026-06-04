package in.rcard.fes.copy.adapter

import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.application.{RegisterCopyError, RegisterCopyUseCase}
import in.rcard.fes.copy.domain.Domain.*
import in.rcard.yaes.Raise
import in.rcard.yaes.Random
import in.rcard.yaes.Sync
import in.rcard.yaes.http.core.Method.POST
import in.rcard.yaes.http.server.Request
import in.rcard.yaes.http.server.Routes as YaesRoutes
import in.rcard.yaes.raises
import in.rcard.yaes.test.scalatest.RandomSpec
import in.rcard.yaes.test.scalatest.SyncSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private val REGISTER_COPY_REQUEST_JSON = s"""{"isbn": "$FOUNDATION_ISBN_VALUE"}"""

private val REGISTER_ALREADY_REGISTERED_COPY_REQUEST_JSON =
  s"""{"isbn": "$ALREADY_REGISTERED_ISBN_VALUE"}"""

private val REGISTER_COPY_INVALID_ISBN_REQUEST_JSON = """{"isbn": "not-valid"}"""

private val REGISTER_COPY_UNEXPECTED_ERROR_REQUEST_JSON = s"""{"isbn": "$UNEXPECTED_ISBN_VALUE"}"""

private val REGISTER_COPY_NOT_IN_CATALOG_REQUEST_JSON =
  s"""{"isbn": "$NOT_IN_CATALOG_ISBN_VALUE"}"""

private val REGISTER_COPY_PARSING_ERROR_RESPONSE_JSON =
  """{"title":"Invalid request body","detail":"The request body could not be parsed. Please check the syntax.","errors":[{"detail":"expected null got 'not_va...' (line 1, column 1)"}]}"""

private val REGISTER_COPY_INVALID_ISBN_VALIDATION_ERROR_RESPONSE_JSON =
  """{"title":"Validation error","detail":"The request body is not valid. Please check the errors for more details.","errors":[{"detail":"DecodingFailure at .isbn: Should be a valid ISBN-13"}]}"""

private val REGISTER_ALREADY_REGISTERED_COPY_VALIDATION_ERROR_RESPONSE_JSON =
  """{"title":"Conflict","detail":"Copy already registered.","errors":[{"detail":"The copy with id 'copy1' is already registered."}]}"""

private val REGISTER_COPY_UNEXPECTED_ERROR_RESPONSE_JSON =
  """{"title":"Unexpected error","detail":"An unexpected error occurred.","errors":[{"detail":"Unexpected error"}]}"""

private val REGISTER_COPY_NOT_IN_CATALOG_RESPONSE_JSON =
  s"""{"title":"Not Found","detail":"The requested ISBN was not found in the catalog.","errors":[{"detail":"ISBN '${NOT_IN_CATALOG_ISBN_VALUE}' not found in the catalog."}]}"""

class RegisterCopyRouteSpec extends AnyFlatSpec with SyncSpec with RandomSpec with Matchers {

  private val mockedRegisterCopyUseCase = new RegisterCopyUseCase {
    override def registerCopy(isbn: ISBN)(using Random, Sync): CopyId raises RegisterCopyError =
      isbn match {
        case ALREADY_REGISTERED_ISBN =>
          Raise.raise(RegisterCopyError.AlreadyRegistered(COPY_ID))
        case UNEXPECTED_ISBN =>
          Raise.raise(RegisterCopyError.UnexpectedError("Unexpected error"))
        case NOT_IN_CATALOG_ISBN =>
          Raise.raise(RegisterCopyError.CopyNotFoundInCatalog(NOT_IN_CATALOG_ISBN))
        case FOUNDATION_ISBN =>
          COPY_ID
      }
  }

  private val underTest: Sync ?=> YaesRoutes = YaesRoutes(
    RegisterCopyRoute(mockedRegisterCopyUseCase).registerCopyRoute
  )

  "RegisterCopyRoute" should "return 201 if the copy is registered successfully" in withSync {

    val request = Request(POST, "/copies", Map.empty, REGISTER_COPY_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 201
    actualResponse.body shouldBe "\"Ok\""
  }

  it should "return 400 if the DTO is not valid" in withSync {

    val request = Request(POST, "/copies", Map.empty, "not_valid", Map.empty)

    val actualResponse = underTest.handle(request)

    println(actualResponse.body)

    actualResponse.status shouldBe 400
    actualResponse.body shouldBe REGISTER_COPY_PARSING_ERROR_RESPONSE_JSON
  }

  it should "return 400 if the ISBN is invalid" in withSync {

    val request =
      Request(POST, "/copies", Map.empty, REGISTER_COPY_INVALID_ISBN_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 400
    actualResponse.body shouldBe REGISTER_COPY_INVALID_ISBN_VALIDATION_ERROR_RESPONSE_JSON
  }

  it should "return 409 if the copy is already registered" in withSync {

    val request =
      Request(POST, "/copies", Map.empty, REGISTER_ALREADY_REGISTERED_COPY_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
    actualResponse.body shouldBe REGISTER_ALREADY_REGISTERED_COPY_VALIDATION_ERROR_RESPONSE_JSON
  }

  it should "return 500 if the use case raises an unexpected error" in withSync {

    val request =
      Request(POST, "/copies", Map.empty, REGISTER_COPY_UNEXPECTED_ERROR_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 500
    actualResponse.body shouldBe REGISTER_COPY_UNEXPECTED_ERROR_RESPONSE_JSON
  }

  it should "return 400 if the ISBN is not found in the catalog" in withSync {

    val request =
      Request(POST, "/copies", Map.empty, REGISTER_COPY_NOT_IN_CATALOG_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 400
    actualResponse.body shouldBe REGISTER_COPY_NOT_IN_CATALOG_RESPONSE_JSON
  }
}

package in.rcard.fes.copy.application

import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.domain.Domain.*
import in.rcard.fes.copy.domain.Error
import in.rcard.fes.copy.domain.Error.AlreadyRegistered
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase
import in.rcard.yaes.http.core.Method.POST
import in.rcard.yaes.http.server.{Request, Routes as YaesRoutes}
import in.rcard.yaes.{Raise, raises}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private val REGISTER_COPY_REQUEST_JSON = s"""{
  "isbn": "$FOUNDATION_ISBN_VALUE",
  "title": "$FOUNDATION_TITLE_VALUE",
  "author": "$FOUNDATION_AUTHOR_VALUE"
}"""

private val REGISTER_ALREADY_REGISTERED_COPY_REQUEST_JSON = s"""{
  "isbn": "$ALREADY_REGISTERED_ISBN_VALUE",
  "title": "$FOUNDATION_TITLE_VALUE",
  "author": "$FOUNDATION_AUTHOR_VALUE"
}"""

private val REGISTER_COPY_EMPTY_TITLE_REQUEST_JSON = """{
  "isbn": "not-valid",
  "title": "",
  "author": "Isaac Asimov"
}"""

private val REGISTER_COPY_UNEXPECTED_ERROR_REQUEST_JSON = s"""{
  "isbn": "$UNEXPECTED_ISBN_VALUE",
  "title": "$FOUNDATION_TITLE_VALUE",
  "author": "$FOUNDATION_AUTHOR_VALUE"
}"""

private val REGISTER_COPY_PARSING_ERROR_RESPONSE_JSON =
  """{"title":"Validation error","detail":"The request body is not valid. Please check the errors for more details.","errors":[{"detail":"DecodingFailure at .isbn: Missing required field"}]}"""

private val REGISTER_COPY_EMPTY_TITLE_VALIDATION_ERROR_RESPONSE_JSON =
  """{"title":"Validation error","detail":"The request body is not valid. Please check the errors for more details.","errors":[{"detail":"DecodingFailure at .isbn: Should be a valid ISBN-13"}]}"""

private val REGISTER_ALREADY_REGISTERED_COPY_VALIDATION_ERROR_RESPONSE_JSON =
  """{"title":"Conflict","detail":"Copy already registered.","errors":[{"detail":"The copy with id 'copy1' is already registered."}]}"""

private val REGISTER_COPY_UNEXPECTED_ERROR_RESPONSE_JSON =
  """{"title":"Unexpected error","detail":"An unexpected error occurred.","errors":[{"detail":"Unexpected error"}]}"""

class RegisterCopyRouteSpec extends AnyFlatSpec with Matchers {

  private val mockedRegisterCopyUseCase = new RegisterCopyUseCase {
    override def registerCopy(copyToRegister: RegisterCopyUseCase.CopyToRegister): CopyId raises
      Error = copyToRegister.isbn match {
      case ALREADY_REGISTERED_ISBN =>
        Raise.raise(AlreadyRegistered(COPY_ID))
      case UNEXPECTED_ISBN =>
        Raise.raise(Error.UnexpectedError("Unexpected error"))
      case FOUNDATION_ISBN =>
        COPY_ID
    }
  }

  private val underTest = YaesRoutes(RegisterCopyRoute(mockedRegisterCopyUseCase).registerCopyRoute)

  "RegisterCopyRoute" should "return 201 if the copy is registered successfully" in {

    val request = Request(POST, "/copies", Map.empty, REGISTER_COPY_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 201
    actualResponse.body shouldBe "\"Ok\""
  }

  it should "return 400 if the DTO is not valid" in {

    val request = Request(POST, "/copies", Map.empty, "{}", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 400
    actualResponse.body shouldBe REGISTER_COPY_PARSING_ERROR_RESPONSE_JSON
  }

  it should "return 400 if the use case raises an error" in {

    val request =
      Request(POST, "/copies", Map.empty, REGISTER_COPY_EMPTY_TITLE_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 400
    actualResponse.body shouldBe REGISTER_COPY_EMPTY_TITLE_VALIDATION_ERROR_RESPONSE_JSON
  }

  it should "return 409 if the copy is already registered" in {

    val request =
      Request(POST, "/copies", Map.empty, REGISTER_ALREADY_REGISTERED_COPY_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
    actualResponse.body shouldBe REGISTER_ALREADY_REGISTERED_COPY_VALIDATION_ERROR_RESPONSE_JSON
  }

  it should "return 500 if the use case raises an unexpected error" in {

    val request =
      Request(POST, "/copies", Map.empty, REGISTER_COPY_UNEXPECTED_ERROR_REQUEST_JSON, Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 500
    actualResponse.body shouldBe REGISTER_COPY_UNEXPECTED_ERROR_RESPONSE_JSON
  }
}

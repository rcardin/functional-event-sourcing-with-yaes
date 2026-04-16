package in.rcard.fes.copy.application

import in.rcard.fes.copy.application.Routes.RegisterCopyRoute
import in.rcard.fes.copy.domain.Domain.*
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase
import in.rcard.yaes.Reader
import in.rcard.yaes.http.core.Method.POST
import in.rcard.yaes.http.server.{Request, Routes as YaesRoutes}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private val COPY_ID                    = CopyId("copy1")
private val REGISTER_COPY_REQUEST_JSON = """{
  "isbn": "978-3-954-76392-4",
  "title": "Foundation",
  "author": "Isaac Asimov"
}"""
private val REGISTER_COPY_EMPTY_TITLE_REQUEST_JSON = """{
  "isbn": "not-valid",
  "title": "",
  "author": "Isaac Asimov"
}"""

class RegisterCopyRouteSpec extends AnyFlatSpec with Matchers {

  private val underTest: Reader[RegisterCopyUseCase] ?=> RegisterCopyRoute = RegisterCopyRoute()

  "RegisterCopyRoute" should "return 201 if the copy is registered successfully" in {

    val registerCopyUseCase = new RegisterCopyUseCase {
      override def registerCopy(): CopyId = COPY_ID
    }

    val request = Request(POST, "/copies", Map.empty, REGISTER_COPY_REQUEST_JSON, Map.empty)

    // FIXME The DI is a bit verbose
    val actualResponse = YaesRoutes(Reader.run(registerCopyUseCase) {
      underTest.registerCopyRoute
    }).handle(request)

    actualResponse.status shouldBe 201
    actualResponse.body shouldBe "\"Ok\""
  }

  it should "return 400 if the DTO is not valid" in {

    val registerCopyUseCase = new RegisterCopyUseCase {
      override def registerCopy(): CopyId = fail("This should not be called")
    }

    val request = Request(POST, "/copies", Map.empty, "{}", Map.empty)

    val actualResponse = YaesRoutes(Reader.run(registerCopyUseCase) {
      underTest.registerCopyRoute
    }).handle(request)

    actualResponse.status shouldBe 400
    actualResponse.body shouldBe "\"Ko\""
  }

  it should "return 400 if the use case raises an error" in {

    val registerCopyUseCase = new RegisterCopyUseCase {
      override def registerCopy(): CopyId = fail("This should not be called")
    }

    val request = Request(POST, "/copies", Map.empty, REGISTER_COPY_EMPTY_TITLE_REQUEST_JSON, Map.empty)

    val actualResponse = YaesRoutes(Reader.run(registerCopyUseCase) {
      underTest.registerCopyRoute
    }).handle(request)

    actualResponse.status shouldBe 400
    actualResponse.body shouldBe "\"Ko\""
  }
}

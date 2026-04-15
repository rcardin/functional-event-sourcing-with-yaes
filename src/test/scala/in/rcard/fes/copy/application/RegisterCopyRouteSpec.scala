package in.rcard.fes.copy.application

import in.rcard.fes.copy.application.Routes.RegisterCopyRoute
import in.rcard.fes.copy.domain.Domain.*
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase
import in.rcard.yaes.Reader
import in.rcard.yaes.http.core.Method.POST
import in.rcard.yaes.http.server.{Request, Routes as YaesRoutes}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

private val COPY_ID = CopyId("copy1")
private val REGISTER_COPY_REQUEST_JSON = """{
  "isbn": "978-3-954-76392-4",
  "title": "Foundation",
  "author": "Isaac Asimov"
}"""

class RegisterCopyRouteSpec extends AnyFlatSpec with Matchers {

  private val underTest: Reader[RegisterCopyUseCase] ?=> RegisterCopyRoute = RegisterCopyRoute()

  "RegisterCopyRoute" should "be created successfully" in {

    val registerCopyUseCase = new RegisterCopyUseCase {
      override def registerCopy(): CopyId = COPY_ID
    }

    val request = Request(POST, "/copies", Map.empty, REGISTER_COPY_REQUEST_JSON, Map.empty)

    val actualResponse = YaesRoutes(Reader.run(registerCopyUseCase) {
      underTest.registerCopyRoute
    }).handle(request)

    actualResponse.status shouldBe 201
    actualResponse.body shouldBe "\"Ok\""
  }
}

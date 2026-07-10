package in.rcard.fes.copy.adapter

import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.application.{RemoveCopyError, RemoveCopyUseCase}
import in.rcard.fes.copy.domain.Domain.*
import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.http.core.Method.POST
import in.rcard.yaes.http.server.Request
import in.rcard.yaes.http.server.Routes as YaesRoutes
import in.rcard.yaes.raises
import in.rcard.yaes.test.scalatest.SyncSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RemoveCopyRouteSpec extends AnyFlatSpec with SyncSpec with Matchers {

  private val mockedRemoveCopyUseCase = new RemoveCopyUseCase {
    override def remove(id: CopyId)(using Sync): Unit raises RemoveCopyError =
      id match {
        case NOT_REGISTERED_COPY_ID =>
          Raise.raise(RemoveCopyError.CopyNotFound(NOT_REGISTERED_COPY_ID))
        case ALREADY_REMOVED_COPY_ID =>
          Raise.raise(RemoveCopyError.CopyIsRemoved(ALREADY_REMOVED_COPY_ID))
        case UNEXPECTED_COPY_ID =>
          Raise.raise(RemoveCopyError.UnexpectedError("Boom"))
        case _ =>
          ()
      }
  }

  private val underTest: Sync ?=> YaesRoutes = YaesRoutes(
    RemoveCopyRoute(mockedRemoveCopyUseCase).removeCopyRoute
  )

  "RemoveCopyRoute" should "return 200 if the copy is removed successfully" in withSync {
    val request = Request(POST, s"/copies/$COPY_ID_VALUE/removed", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 200
    actualResponse.body shouldBe "\"Ok\""
  }

  it should "return 404 if the copy was never registered" in withSync {
    val request =
      Request(POST, s"/copies/$NOT_REGISTERED_COPY_ID_VALUE/removed", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 404
  }

  it should "return 409 if the copy is already removed" in withSync {
    val request =
      Request(POST, s"/copies/$ALREADY_REMOVED_COPY_ID_VALUE/removed", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
  }

  it should "return 500 if the use case raises an unexpected error" in withSync {
    val request =
      Request(POST, s"/copies/$UNEXPECTED_COPY_ID_VALUE/removed", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 500
  }
}

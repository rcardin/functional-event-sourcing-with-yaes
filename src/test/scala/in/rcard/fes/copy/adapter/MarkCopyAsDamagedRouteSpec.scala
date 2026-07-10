package in.rcard.fes.copy.adapter

import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.application.{MarkCopyAsDamagedError, MarkCopyAsDamagedUseCase}
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

class MarkCopyAsDamagedRouteSpec extends AnyFlatSpec with SyncSpec with Matchers {

  private val mockedMarkCopyAsDamagedUseCase = new MarkCopyAsDamagedUseCase {
    override def markAsDamaged(id: CopyId)(using Sync): Unit raises MarkCopyAsDamagedError =
      id match {
        case NOT_REGISTERED_COPY_ID =>
          Raise.raise(MarkCopyAsDamagedError.CopyNotFound(NOT_REGISTERED_COPY_ID))
        case ALREADY_DAMAGED_COPY_ID =>
          Raise.raise(MarkCopyAsDamagedError.AlreadyDamaged(ALREADY_DAMAGED_COPY_ID))
        case ALREADY_LOST_COPY_ID =>
          Raise.raise(MarkCopyAsDamagedError.CopyIsLost(ALREADY_LOST_COPY_ID))
        case ALREADY_REMOVED_COPY_ID =>
          Raise.raise(MarkCopyAsDamagedError.CopyIsRemoved(ALREADY_REMOVED_COPY_ID))
        case UNEXPECTED_COPY_ID =>
          Raise.raise(MarkCopyAsDamagedError.UnexpectedError("Boom"))
        case _ =>
          ()
      }
  }

  private val underTest: Sync ?=> YaesRoutes = YaesRoutes(
    MarkCopyAsDamagedRoute(mockedMarkCopyAsDamagedUseCase).markCopyAsDamagedRoute
  )

  "MarkCopyAsDamagedRoute" should "return 200 if the copy is marked as damaged successfully" in withSync {
    val request = Request(POST, s"/copies/$COPY_ID_VALUE/damaged", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 200
    actualResponse.body shouldBe "\"Ok\""
  }

  it should "return 404 if the copy was never registered" in withSync {
    val request =
      Request(POST, s"/copies/$NOT_REGISTERED_COPY_ID_VALUE/damaged", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 404
  }

  it should "return 409 if the copy is already damaged" in withSync {
    val request =
      Request(POST, s"/copies/$ALREADY_DAMAGED_COPY_ID_VALUE/damaged", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
  }

  it should "return 409 if the copy is lost" in withSync {
    val request =
      Request(POST, s"/copies/$ALREADY_LOST_COPY_ID_VALUE/damaged", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
  }

  it should "return 500 if the use case raises an unexpected error" in withSync {
    val request =
      Request(POST, s"/copies/$UNEXPECTED_COPY_ID_VALUE/damaged", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 500
  }

  it should "return 409 if the copy is removed" in withSync {
    val request =
      Request(POST, s"/copies/$ALREADY_REMOVED_COPY_ID_VALUE/damaged", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
  }
}

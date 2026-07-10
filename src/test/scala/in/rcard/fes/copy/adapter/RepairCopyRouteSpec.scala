package in.rcard.fes.copy.adapter

import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.application.{RepairCopyError, RepairCopyUseCase}
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

class RepairCopyRouteSpec extends AnyFlatSpec with SyncSpec with Matchers {

  private val mockedRepairCopyUseCase = new RepairCopyUseCase {
    override def repair(id: CopyId)(using Sync): Unit raises RepairCopyError =
      id match {
        case NOT_REGISTERED_COPY_ID =>
          Raise.raise(RepairCopyError.CopyNotFound(NOT_REGISTERED_COPY_ID))
        case NOT_DAMAGED_COPY_ID =>
          Raise.raise(RepairCopyError.NotDamaged(NOT_DAMAGED_COPY_ID))
        case ALREADY_REMOVED_COPY_ID =>
          Raise.raise(RepairCopyError.CopyIsRemoved(ALREADY_REMOVED_COPY_ID))
        case UNEXPECTED_COPY_ID =>
          Raise.raise(RepairCopyError.UnexpectedError("Boom"))
        case _ =>
          ()
      }
  }

  private val underTest: Sync ?=> YaesRoutes = YaesRoutes(
    RepairCopyRoute(mockedRepairCopyUseCase).repairCopyRoute
  )

  "RepairCopyRoute" should "return 200 if the copy is repaired successfully" in withSync {
    val request = Request(POST, s"/copies/$COPY_ID_VALUE/repaired", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 200
    actualResponse.body shouldBe "\"Ok\""
  }

  it should "return 404 if the copy was never registered" in withSync {
    val request =
      Request(POST, s"/copies/$NOT_REGISTERED_COPY_ID_VALUE/repaired", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 404
  }

  it should "return 409 if the copy is not damaged" in withSync {
    val request =
      Request(POST, s"/copies/$NOT_DAMAGED_COPY_ID_VALUE/repaired", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
  }

  it should "return 500 if the use case raises an unexpected error" in withSync {
    val request =
      Request(POST, s"/copies/$UNEXPECTED_COPY_ID_VALUE/repaired", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 500
  }

  it should "return 409 if the copy is removed" in withSync {
    val request =
      Request(POST, s"/copies/$ALREADY_REMOVED_COPY_ID_VALUE/repaired", Map.empty, "", Map.empty)

    val actualResponse = underTest.handle(request)

    actualResponse.status shouldBe 409
  }
}

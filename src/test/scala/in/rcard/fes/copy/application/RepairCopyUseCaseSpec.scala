package in.rcard.fes.copy.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.copy.Fixtures.*
import in.rcard.fes.copy.domain.Command
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.Error
import in.rcard.fes.copy.domain.Event
import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.raises
import in.rcard.yaes.test.scalatest.RaiseSpec
import in.rcard.yaes.test.scalatest.SyncSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RepairCopyUseCaseSpec
    extends AnyFlatSpec
    with SyncSpec
    with RaiseSpec
    with Matchers {

  val mockCommandHandler: CommandHandler[CopyId, Command, Error, Event] =
    new CommandHandler[CopyId, Command, Error, Event] {
      override def handle(id: CopyId, cmd: Command)(using Sync): Seq[Event] raises Error = cmd match {
        case Command.Repair(copyId) if copyId == NOT_REGISTERED_COPY_ID =>
          Raise.raise(Error.CopyNotFound(copyId))
        case Command.Repair(copyId) if copyId == NOT_DAMAGED_COPY_ID =>
          Raise.raise(Error.NotDamaged(copyId))
        case Command.Repair(copyId) if copyId == ALREADY_REMOVED_COPY_ID =>
          Raise.raise(Error.CopyIsRemoved(copyId))
        case Command.Repair(copyId) if copyId == UNEXPECTED_COPY_ID =>
          Raise.raise(Error.UnexpectedError("Boom"))
        case Command.Repair(copyId) =>
          Seq(Event.Repaired(copyId))
        case _ =>
          Raise.raise(Error.UnexpectedError("Unexpected command"))
      }
    }

  private val underTest = RepairCopyUseCase(mockCommandHandler)

  "RepairCopyUseCase.repair" should "repair a damaged copy" in withSync {
    val actualResult = failOnRaise { underTest.repair(COPY_ID) }

    actualResult shouldBe ()
  }

  it should "translate a domain CopyNotFound error into RepairCopyError.CopyNotFound" in withSync {
    val actualResult = interceptRaised { underTest.repair(NOT_REGISTERED_COPY_ID) }

    actualResult shouldBe RepairCopyError.CopyNotFound(NOT_REGISTERED_COPY_ID)
  }

  it should "translate a domain NotDamaged error into RepairCopyError.NotDamaged" in withSync {
    val actualResult = interceptRaised { underTest.repair(NOT_DAMAGED_COPY_ID) }

    actualResult shouldBe RepairCopyError.NotDamaged(NOT_DAMAGED_COPY_ID)
  }

  it should "translate a domain UnexpectedError into RepairCopyError.UnexpectedError" in withSync {
    val actualResult = interceptRaised { underTest.repair(UNEXPECTED_COPY_ID) }

    actualResult shouldBe RepairCopyError.UnexpectedError("Boom")
  }

  it should "translate a domain CopyIsRemoved error into RepairCopyError.CopyIsRemoved" in withSync {
    val actualResult = interceptRaised { underTest.repair(ALREADY_REMOVED_COPY_ID) }

    actualResult shouldBe RepairCopyError.CopyIsRemoved(ALREADY_REMOVED_COPY_ID)
  }
}

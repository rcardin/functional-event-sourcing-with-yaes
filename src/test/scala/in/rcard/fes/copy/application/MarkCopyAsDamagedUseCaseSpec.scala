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

class MarkCopyAsDamagedUseCaseSpec
    extends AnyFlatSpec
    with SyncSpec
    with RaiseSpec
    with Matchers {

  val mockCommandHandler: CommandHandler[CopyId, Command, Error, Event] =
    new CommandHandler[CopyId, Command, Error, Event] {
      override def handle(id: CopyId, cmd: Command)(using Sync): Seq[Event] raises Error = cmd match {
        case Command.MarkAsDamaged(copyId) if copyId == NOT_REGISTERED_COPY_ID =>
          Raise.raise(Error.CopyNotFound(copyId))
        case Command.MarkAsDamaged(copyId) if copyId == ALREADY_DAMAGED_COPY_ID =>
          Raise.raise(Error.AlreadyDamaged(copyId))
        case Command.MarkAsDamaged(copyId) if copyId == ALREADY_LOST_COPY_ID =>
          Raise.raise(Error.CopyIsLost(copyId))
        case Command.MarkAsDamaged(copyId) if copyId == ALREADY_REMOVED_COPY_ID =>
          Raise.raise(Error.CopyIsRemoved(copyId))
        case Command.MarkAsDamaged(copyId) if copyId == UNEXPECTED_COPY_ID =>
          Raise.raise(Error.UnexpectedError("Boom"))
        case Command.MarkAsDamaged(copyId) =>
          Seq(Event.MarkedAsDamaged(copyId))
        case _ =>
          Raise.raise(Error.UnexpectedError("Unexpected command"))
      }
    }

  private val underTest = MarkCopyAsDamagedUseCase(mockCommandHandler)

  "MarkCopyAsDamagedUseCase.markAsDamaged" should "mark a registered available copy as damaged" in withSync {
    val actualResult = failOnRaise { underTest.markAsDamaged(COPY_ID) }

    actualResult shouldBe ()
  }

  it should "translate a domain CopyNotFound error into MarkCopyAsDamagedError.CopyNotFound" in withSync {
    val actualResult = interceptRaised { underTest.markAsDamaged(NOT_REGISTERED_COPY_ID) }

    actualResult shouldBe MarkCopyAsDamagedError.CopyNotFound(NOT_REGISTERED_COPY_ID)
  }

  it should "translate a domain AlreadyDamaged error into MarkCopyAsDamagedError.AlreadyDamaged" in withSync {
    val actualResult = interceptRaised { underTest.markAsDamaged(ALREADY_DAMAGED_COPY_ID) }

    actualResult shouldBe MarkCopyAsDamagedError.AlreadyDamaged(ALREADY_DAMAGED_COPY_ID)
  }

  it should "translate a domain CopyIsLost error into MarkCopyAsDamagedError.CopyIsLost" in withSync {
    val actualResult = interceptRaised { underTest.markAsDamaged(ALREADY_LOST_COPY_ID) }

    actualResult shouldBe MarkCopyAsDamagedError.CopyIsLost(ALREADY_LOST_COPY_ID)
  }

  it should "translate a domain UnexpectedError into MarkCopyAsDamagedError.UnexpectedError" in withSync {
    val actualResult = interceptRaised { underTest.markAsDamaged(UNEXPECTED_COPY_ID) }

    actualResult shouldBe MarkCopyAsDamagedError.UnexpectedError("Boom")
  }

  it should "translate a domain CopyIsRemoved error into MarkCopyAsDamagedError.CopyIsRemoved" in withSync {
    val actualResult = interceptRaised { underTest.markAsDamaged(ALREADY_REMOVED_COPY_ID) }

    actualResult shouldBe MarkCopyAsDamagedError.CopyIsRemoved(ALREADY_REMOVED_COPY_ID)
  }
}

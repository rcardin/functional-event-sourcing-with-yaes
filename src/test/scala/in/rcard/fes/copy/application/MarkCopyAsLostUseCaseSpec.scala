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

class MarkCopyAsLostUseCaseSpec
    extends AnyFlatSpec
    with SyncSpec
    with RaiseSpec
    with Matchers {

  val mockCommandHandler: CommandHandler[CopyId, Command, Error, Event] =
    new CommandHandler[CopyId, Command, Error, Event] {
      override def handle(id: CopyId, cmd: Command)(using Sync): Seq[Event] raises Error = cmd match {
        case Command.MarkAsLost(copyId) if copyId == NOT_REGISTERED_COPY_ID =>
          Raise.raise(Error.CopyNotFound(copyId))
        case Command.MarkAsLost(copyId) if copyId == ALREADY_LOST_COPY_ID =>
          Raise.raise(Error.AlreadyLost(copyId))
        case Command.MarkAsLost(copyId) if copyId == ALREADY_REMOVED_COPY_ID =>
          Raise.raise(Error.CopyIsRemoved(copyId))
        case Command.MarkAsLost(copyId) if copyId == UNEXPECTED_COPY_ID =>
          Raise.raise(Error.UnexpectedError("Boom"))
        case Command.MarkAsLost(copyId) =>
          Seq(Event.MarkedAsLost(copyId))
        case _ =>
          Raise.raise(Error.UnexpectedError("Unexpected command"))
      }
    }

  private val underTest = MarkCopyAsLostUseCase(mockCommandHandler)

  "MarkCopyAsLostUseCase.markAsLost" should "mark a registered available copy as lost" in withSync {
    val actualResult = failOnRaise { underTest.markAsLost(COPY_ID) }

    actualResult shouldBe ()
  }

  it should "translate a domain CopyNotFound error into MarkCopyAsLostError.CopyNotFound" in withSync {
    val actualResult = interceptRaised { underTest.markAsLost(NOT_REGISTERED_COPY_ID) }

    actualResult shouldBe MarkCopyAsLostError.CopyNotFound(NOT_REGISTERED_COPY_ID)
  }

  it should "translate a domain AlreadyLost error into MarkCopyAsLostError.AlreadyLost" in withSync {
    val actualResult = interceptRaised { underTest.markAsLost(ALREADY_LOST_COPY_ID) }

    actualResult shouldBe MarkCopyAsLostError.AlreadyLost(ALREADY_LOST_COPY_ID)
  }

  it should "translate a domain UnexpectedError into MarkCopyAsLostError.UnexpectedError" in withSync {
    val actualResult = interceptRaised { underTest.markAsLost(UNEXPECTED_COPY_ID) }

    actualResult shouldBe MarkCopyAsLostError.UnexpectedError("Boom")
  }

  it should "translate a domain CopyIsRemoved error into MarkCopyAsLostError.CopyIsRemoved" in withSync {
    val actualResult = interceptRaised { underTest.markAsLost(ALREADY_REMOVED_COPY_ID) }

    actualResult shouldBe MarkCopyAsLostError.CopyIsRemoved(ALREADY_REMOVED_COPY_ID)
  }
}

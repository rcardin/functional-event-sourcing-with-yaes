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

class RemoveCopyUseCaseSpec
    extends AnyFlatSpec
    with SyncSpec
    with RaiseSpec
    with Matchers {

  val mockCommandHandler: CommandHandler[CopyId, Command, Error, Event] =
    new CommandHandler[CopyId, Command, Error, Event] {
      override def handle(id: CopyId, cmd: Command)(using Sync): Seq[Event] raises Error = cmd match {
        case Command.Remove(copyId) if copyId == NOT_REGISTERED_COPY_ID =>
          Raise.raise(Error.CopyNotFound(copyId))
        case Command.Remove(copyId) if copyId == ALREADY_REMOVED_COPY_ID =>
          Raise.raise(Error.CopyIsRemoved(copyId))
        case Command.Remove(copyId) if copyId == UNEXPECTED_COPY_ID =>
          Raise.raise(Error.UnexpectedError("Boom"))
        case Command.Remove(copyId) =>
          Seq(Event.Removed(copyId))
        case _ =>
          Raise.raise(Error.UnexpectedError("Unexpected command"))
      }
    }

  private val underTest = RemoveCopyUseCase(mockCommandHandler)

  "RemoveCopyUseCase.remove" should "remove a registered copy" in withSync {
    val actualResult = failOnRaise { underTest.remove(COPY_ID) }

    actualResult shouldBe ()
  }

  it should "translate a domain CopyNotFound error into RemoveCopyError.CopyNotFound" in withSync {
    val actualResult = interceptRaised { underTest.remove(NOT_REGISTERED_COPY_ID) }

    actualResult shouldBe RemoveCopyError.CopyNotFound(NOT_REGISTERED_COPY_ID)
  }

  it should "translate a domain CopyIsRemoved error into RemoveCopyError.CopyIsRemoved" in withSync {
    val actualResult = interceptRaised { underTest.remove(ALREADY_REMOVED_COPY_ID) }

    actualResult shouldBe RemoveCopyError.CopyIsRemoved(ALREADY_REMOVED_COPY_ID)
  }

  it should "translate a domain UnexpectedError into RemoveCopyError.UnexpectedError" in withSync {
    val actualResult = interceptRaised { underTest.remove(UNEXPECTED_COPY_ID) }

    actualResult shouldBe RemoveCopyError.UnexpectedError("Boom")
  }

  it should "translate any other domain error into RemoveCopyError.UnexpectedError" in withSync {
    val handler = new CommandHandler[CopyId, Command, Error, Event] {
      override def handle(id: CopyId, cmd: Command)(using Sync): Seq[Event] raises Error =
        Raise.raise(Error.AlreadyLost(id))
    }

    val actualResult = interceptRaised { RemoveCopyUseCase(handler).remove(COPY_ID) }

    actualResult shouldBe RemoveCopyError.UnexpectedError("Unexpected state while removing a copy")
  }

  it should "raise RemoveCopyError.UnexpectedError for an unexpected event sequence" in withSync {
    val handler = new CommandHandler[CopyId, Command, Error, Event] {
      override def handle(id: CopyId, cmd: Command)(using Sync): Seq[Event] raises Error =
        Seq(Event.Repaired(id))
    }

    val actualResult = interceptRaised { RemoveCopyUseCase(handler).remove(COPY_ID) }

    actualResult shouldBe RemoveCopyError.UnexpectedError("Unexpected state after removing a copy")
  }
}

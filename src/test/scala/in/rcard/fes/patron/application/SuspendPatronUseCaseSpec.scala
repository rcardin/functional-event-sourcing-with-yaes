package in.rcard.fes.patron.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.patron.Fixtures.*
import in.rcard.fes.patron.domain.Command
import in.rcard.fes.patron.domain.Domain.PatronId
import in.rcard.fes.patron.domain.Error
import in.rcard.fes.patron.domain.Event
import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.raises
import in.rcard.yaes.test.scalatest.RaiseSpec
import in.rcard.yaes.test.scalatest.SyncSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SuspendPatronUseCaseSpec extends AnyFlatSpec with SyncSpec with RaiseSpec with Matchers {

  val mockCommandHandler: CommandHandler[PatronId, Command, Error, Event] =
    new CommandHandler[PatronId, Command, Error, Event] {
      override def handle(id: PatronId, cmd: Command)(using Sync): Seq[Event] raises Error = cmd match {
        case Command.Suspend(cardId) if cardId == NOT_REGISTERED_CARD_ID =>
          Raise.raise(Error.PatronNotFound(cardId))
        case Command.Suspend(cardId) if cardId == ALREADY_SUSPENDED_CARD_ID =>
          Raise.raise(Error.AlreadySuspended(cardId))
        case Command.Suspend(cardId) if cardId == UNEXPECTED_CARD_ID =>
          Raise.raise(Error.UnexpectedError("boom"))
        case Command.Suspend(cardId) if cardId == CARD_ID =>
          Seq(Event.Suspended(cardId))
        case _ =>
          Raise.raise(Error.UnexpectedError("Unexpected state after suspending a patron"))
      }
    }

  private val underTest = SuspendPatronUseCase(mockCommandHandler)

  "SuspendPatronUseCase.suspend" should "suspend an active patron successfully" in withSync {
    failOnRaise { underTest.suspend(CARD_ID) }
  }

  it should "raise PatronNotFound if the patron was never registered" in withSync {
    val actualResult = interceptRaised { underTest.suspend(NOT_REGISTERED_CARD_ID) }

    actualResult shouldBe SuspendPatronError.PatronNotFound(NOT_REGISTERED_CARD_ID)
  }

  it should "raise AlreadySuspended if the patron is already suspended" in withSync {
    val actualResult = interceptRaised { underTest.suspend(ALREADY_SUSPENDED_CARD_ID) }

    actualResult shouldBe SuspendPatronError.AlreadySuspended(ALREADY_SUSPENDED_CARD_ID)
  }

  it should "raise an UnexpectedError if the command handler raises an unexpected domain error" in withSync {
    val actualResult = interceptRaised { underTest.suspend(UNEXPECTED_CARD_ID) }

    actualResult shouldBe SuspendPatronError.UnexpectedError("boom")
  }
}

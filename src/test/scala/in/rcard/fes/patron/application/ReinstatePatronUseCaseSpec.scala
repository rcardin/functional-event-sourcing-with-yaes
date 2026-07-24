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

class ReinstatePatronUseCaseSpec extends AnyFlatSpec with SyncSpec with RaiseSpec with Matchers {

  val mockCommandHandler: CommandHandler[PatronId, Command, Error, Event] =
    new CommandHandler[PatronId, Command, Error, Event] {
      override def handle(id: PatronId, cmd: Command)(using Sync): Seq[Event] raises Error = cmd match {
        case Command.Reinstate(cardId) if cardId == NOT_REGISTERED_CARD_ID =>
          Raise.raise(Error.PatronNotFound(cardId))
        case Command.Reinstate(cardId) if cardId == CARD_ID =>
          Raise.raise(Error.NotSuspended(cardId))
        case Command.Reinstate(cardId) if cardId == UNEXPECTED_CARD_ID =>
          Raise.raise(Error.UnexpectedError("boom"))
        case Command.Reinstate(cardId) if cardId == ALREADY_SUSPENDED_CARD_ID =>
          Seq(Event.Reinstated(cardId))
        case _ =>
          Raise.raise(Error.UnexpectedError("Unexpected state after reinstating a patron"))
      }
    }

  private val underTest = ReinstatePatronUseCase(mockCommandHandler)

  "ReinstatePatronUseCase.reinstate" should "reinstate a suspended patron successfully" in withSync {
    failOnRaise { underTest.reinstate(ALREADY_SUSPENDED_CARD_ID) }
  }

  it should "raise PatronNotFound if the patron was never registered" in withSync {
    val actualResult = interceptRaised { underTest.reinstate(NOT_REGISTERED_CARD_ID) }

    actualResult shouldBe ReinstatePatronError.PatronNotFound(NOT_REGISTERED_CARD_ID)
  }

  it should "raise NotSuspended if the patron is not suspended" in withSync {
    val actualResult = interceptRaised { underTest.reinstate(CARD_ID) }

    actualResult shouldBe ReinstatePatronError.NotSuspended(CARD_ID)
  }

  it should "raise an UnexpectedError if the command handler raises an unexpected domain error" in withSync {
    val actualResult = interceptRaised { underTest.reinstate(UNEXPECTED_CARD_ID) }

    actualResult shouldBe ReinstatePatronError.UnexpectedError("boom")
  }
}

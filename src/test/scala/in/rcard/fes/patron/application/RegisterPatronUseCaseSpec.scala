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

class RegisterPatronUseCaseSpec extends AnyFlatSpec with SyncSpec with RaiseSpec with Matchers {

  val mockCommandHandler: CommandHandler[PatronId, Command, Error, Event] =
    new CommandHandler[PatronId, Command, Error, Event] {
      override def handle(id: PatronId, cmd: Command)(using Sync): Seq[Event] raises Error = cmd match {
        case Command.Register(cardId, _, _) if cardId == ALREADY_REGISTERED_CARD_ID =>
          Raise.raise(Error.AlreadyRegistered(cardId))
        case Command.Register(cardId, name, borrowLimit) if cardId == CARD_ID =>
          Seq(Event.Registered(cardId, name, borrowLimit))
        case _ =>
          Raise.raise(Error.UnexpectedError("Unexpected state after patron registration"))
      }
    }

  private val underTest = RegisterPatronUseCase(mockCommandHandler)

  "RegisterPatronUseCase.registerPatron" should "register a patron successfully if it is not already registered" in withSync {
    val actualResult = failOnRaise { underTest.registerPatron(CARD_ID, PATRON_NAME, BORROW_LIMIT) }

    actualResult shouldBe CARD_ID
  }

  it should "not register a patron if it is already registered" in withSync {
    val actualResult =
      interceptRaised { underTest.registerPatron(ALREADY_REGISTERED_CARD_ID, PATRON_NAME, BORROW_LIMIT) }

    actualResult shouldBe RegisterPatronError.AlreadyRegistered(ALREADY_REGISTERED_CARD_ID)
  }

  it should "raise an error if the command handler raises an unexpected error" in withSync {
    val actualResult =
      interceptRaised { underTest.registerPatron(UNEXPECTED_CARD_ID, PATRON_NAME, BORROW_LIMIT) }

    actualResult shouldBe RegisterPatronError.UnexpectedError(
      "Unexpected state after patron registration"
    )
  }
}

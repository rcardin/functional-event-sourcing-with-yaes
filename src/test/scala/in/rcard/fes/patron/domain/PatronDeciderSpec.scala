package in.rcard.fes.patron.domain

import in.rcard.fes.patron.domain.Domain.*
import in.rcard.fes.patron.domain.{Command, Error, Event}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.language.postfixOps
import in.rcard.yaes.test.scalatest.RaiseSpec

private val CARD_ID      = PatronId("card1")
private val PATRON_NAME  = PatronName("Ada Lovelace")
private val BORROW_LIMIT = BorrowLimit(5)

class PatronDeciderSpec extends AnyFlatSpec with RaiseSpec with Matchers {

  private val underTest = new PatronDecider

  "PatronDecider.decide" should "register a patron if it is not already registered" in {
    val command = Command.Register(CARD_ID, PATRON_NAME, BORROW_LIMIT)
    val state   = PatronState.empty

    val actualResult = failOnRaise { underTest.decide(command, state) }

    actualResult should contain only Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)
  }

  it should "not register a patron if it is already registered" in {
    val command = Command.Register(CARD_ID, PATRON_NAME, BORROW_LIMIT)
    val state   = PatronState.empty :+ Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)

    val actualResult = interceptRaised { underTest.decide(command, state) }

    actualResult shouldBe Error.AlreadyRegistered(CARD_ID)
  }

  "PatronDecider.isTerminal" should "return false for an empty state" in {
    underTest.isTerminal(PatronState.empty) shouldBe false
  }

  it should "return false after a patron was registered" in {
    val state = PatronState.empty :+ Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)

    underTest.isTerminal(state) shouldBe false
  }

  "PatronDecider.evolve" should "add the event to the state" in {
    val state = PatronState.empty

    val actualResult = underTest.evolve(state, Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT))

    actualResult should contain only Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)
  }
}

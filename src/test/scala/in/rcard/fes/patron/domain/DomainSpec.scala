package in.rcard.fes.patron.domain

import in.rcard.fes.patron.domain.Domain.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DomainSpec extends AnyFlatSpec with Matchers {

  private val CARD_ID       = PatronId("card1")
  private val OTHER_CARD_ID = PatronId("card2")
  private val PATRON_NAME   = PatronName("Ada Lovelace")
  private val BORROW_LIMIT  = BorrowLimit(5)

  "Domain.currentStatus" should "return NotRegistered for an empty stream" in {
    PatronState.empty.currentStatus(CARD_ID) shouldBe Status.NotRegistered
  }

  it should "return Active after a Registered event" in {
    val state = PatronState.empty :+ Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)

    state.currentStatus(CARD_ID) shouldBe Status.Active
  }

  it should "ignore events for other ids" in {
    val state = PatronState.empty :+ Event.Registered(OTHER_CARD_ID, PATRON_NAME, BORROW_LIMIT)

    state.currentStatus(CARD_ID) shouldBe Status.NotRegistered
  }

  "Domain.isRegistered" should "return true for a registered patron" in {
    val state = PatronState.empty :+ Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)

    state.isRegistered(CARD_ID) shouldBe true
  }

  it should "return false for an empty stream" in {
    PatronState.empty.isRegistered(CARD_ID) shouldBe false
  }

  it should "return false when another patron was registered" in {
    val state = PatronState.empty :+ Event.Registered(OTHER_CARD_ID, PATRON_NAME, BORROW_LIMIT)

    state.isRegistered(CARD_ID) shouldBe false
  }
}

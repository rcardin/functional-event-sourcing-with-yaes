package in.rcard.fes.copy.domain

import in.rcard.fes.copy.domain.Domain.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DomainSpec extends AnyFlatSpec with Matchers {

  private val ID     = CopyId("copy1")
  private val OTHER  = CopyId("copy2")
  private val ISBN_  = ISBN("isbn1")
  private val TITLE_ = Title("title1")
  private val AUTHOR_ = Author("author1")

  "Domain.currentStatus" should "return NotRegistered for an empty stream" in {
    CopyState.empty.currentStatus(ID) shouldBe Status.NotRegistered
  }

  it should "return Available after a Registered event" in {
    val state = CopyState.empty :+ Event.Registered(ID, ISBN_, TITLE_, Seq(AUTHOR_))

    state.currentStatus(ID) shouldBe Status.Available
  }

  it should "return Lost after Registered then MarkedAsLost" in {
    val state = CopyState.empty :+
      Event.Registered(ID, ISBN_, TITLE_, Seq(AUTHOR_)) :+
      Event.MarkedAsLost(ID)

    state.currentStatus(ID) shouldBe Status.Lost
  }

  it should "return Damaged after Registered then MarkedAsDamaged" in {
    val state = CopyState.empty :+
      Event.Registered(ID, ISBN_, TITLE_, Seq(AUTHOR_)) :+
      Event.MarkedAsDamaged(ID)

    state.currentStatus(ID) shouldBe Status.Damaged
  }

  it should "let the last lifecycle event win" in {
    val state = CopyState.empty :+
      Event.Registered(ID, ISBN_, TITLE_, Seq(AUTHOR_)) :+
      Event.MarkedAsDamaged(ID) :+
      Event.MarkedAsLost(ID)

    state.currentStatus(ID) shouldBe Status.Lost
  }

  it should "ignore events for other ids" in {
    val state = CopyState.empty :+
      Event.Registered(OTHER, ISBN_, TITLE_, Seq(AUTHOR_)) :+
      Event.MarkedAsLost(OTHER)

    state.currentStatus(ID) shouldBe Status.NotRegistered
  }
}

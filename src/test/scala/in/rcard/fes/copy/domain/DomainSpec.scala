package in.rcard.fes.copy.domain

import in.rcard.fes.copy.domain.Domain.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DomainSpec extends AnyFlatSpec with Matchers {

  private val COPY_ID       = CopyId("copy1")
  private val OTHER_COPY_ID = CopyId("copy2")
  private val COPY_ISBN     = ISBN("isbn1")
  private val TITLE         = Title("title1")
  private val AUTHOR        = Author("author1")

  "Domain.currentStatus" should "return NotRegistered for an empty stream" in {
    CopyState.empty.currentStatus(COPY_ID) shouldBe Status.NotRegistered
  }

  it should "return Available after a Registered event" in {
    val state = CopyState.empty :+ Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR))

    state.currentStatus(COPY_ID) shouldBe Status.Available
  }

  it should "return Lost after Registered then MarkedAsLost" in {
    val state = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.MarkedAsLost(COPY_ID)

    state.currentStatus(COPY_ID) shouldBe Status.Lost
  }

  it should "return Damaged after Registered then MarkedAsDamaged" in {
    val state = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.MarkedAsDamaged(COPY_ID)

    state.currentStatus(COPY_ID) shouldBe Status.Damaged
  }

  it should "let the last lifecycle event win" in {
    val state = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.MarkedAsDamaged(COPY_ID) :+
      Event.MarkedAsLost(COPY_ID)

    state.currentStatus(COPY_ID) shouldBe Status.Lost
  }

  it should "return Removed after Registered then Removed" in {
    val state = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.Removed(COPY_ID)

    state.currentStatus(COPY_ID) shouldBe Status.Removed
  }

  it should "let a Removed event win over an earlier lifecycle event" in {
    val state = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.MarkedAsDamaged(COPY_ID) :+
      Event.Removed(COPY_ID)

    state.currentStatus(COPY_ID) shouldBe Status.Removed
  }

  "Domain.isRemoved" should "return true for a removed copy" in {
    val state = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.Removed(COPY_ID)

    state.isRemoved(COPY_ID) shouldBe true
  }

  it should "return false for a copy that is not removed" in {
    val state = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.MarkedAsDamaged(COPY_ID)

    state.isRemoved(COPY_ID) shouldBe false
  }

  it should "return false for an empty stream" in {
    CopyState.empty.isRemoved(COPY_ID) shouldBe false
  }

  it should "return false when another copy was removed" in {
    val state = CopyState.empty :+
      Event.Registered(OTHER_COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.Removed(OTHER_COPY_ID)

    state.isRemoved(COPY_ID) shouldBe false
  }

  it should "ignore events for other ids" in {
    val state = CopyState.empty :+
      Event.Registered(OTHER_COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.MarkedAsLost(OTHER_COPY_ID)

    state.currentStatus(COPY_ID) shouldBe Status.NotRegistered
  }
}

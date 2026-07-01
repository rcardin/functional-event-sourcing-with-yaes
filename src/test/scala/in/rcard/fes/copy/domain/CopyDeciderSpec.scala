package in.rcard.fes.copy.domain

import in.rcard.fes.copy.domain.Domain.*
import in.rcard.fes.copy.domain.{Command, Error, Event}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.language.postfixOps
import in.rcard.yaes.test.scalatest.RaiseSpec
import in.rcard.fes.copy.domain.CopyDecider

private val COPY_ID   = CopyId("copy1")
private val COPY_ISBN = ISBN("isbn1")
private val TITLE     = Title("title1")
private val AUTHOR    = Author("author1")

class CopyDeciderSpec extends AnyFlatSpec with RaiseSpec with Matchers {

  private val underTest = new CopyDecider

  "CopyDecider.decide" should "register a copy if it is not already registered" in {
    val command =
      Command.Register(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR))
    val state = CopyState.empty

    val actualResult = failOnRaise { underTest.decide(command, state) }
    actualResult should contain only Event.Registered(
      COPY_ID,
      COPY_ISBN,
      TITLE,
      Seq(AUTHOR)
    )

  }

  it should "not register a copy if it is already registered" in {
    val underTest = new CopyDecider
    val command   =
      Command.Register(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR))
    val state = CopyState.empty :+ Event.Registered(
      COPY_ID,
      COPY_ISBN,
      TITLE,
      Seq(AUTHOR)
    )

    val actualResult = interceptRaised { underTest.decide(command, state) }

    actualResult shouldBe Error.AlreadyRegistered(COPY_ID)
  }

  "CopyDecider.decide" should "mark a registered available copy as lost" in {
    val command = Command.MarkAsLost(COPY_ID)
    val state   = CopyState.empty :+ Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR))

    val actualResult = failOnRaise { underTest.decide(command, state) }

    actualResult should contain only Event.MarkedAsLost(COPY_ID)
  }

  it should "make the copy state lost after applying the MarkedAsLost event" in {
    val command = Command.MarkAsLost(COPY_ID)
    val state   = CopyState.empty :+ Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR))

    val events       = failOnRaise { underTest.decide(command, state) }
    val updatedState = events.foldLeft(state)(underTest.evolve)

    updatedState.isLost(COPY_ID) shouldBe true
  }

  it should "not mark a copy as lost if it was never registered" in {
    val command = Command.MarkAsLost(COPY_ID)
    val state   = CopyState.empty

    val actualResult = interceptRaised { underTest.decide(command, state) }

    actualResult shouldBe Error.CopyNotFound(COPY_ID)
  }

  it should "not mark a copy as lost if it is already lost" in {
    val command = Command.MarkAsLost(COPY_ID)
    val state   = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.MarkedAsLost(COPY_ID)

    val actualResult = interceptRaised { underTest.decide(command, state) }

    actualResult shouldBe Error.AlreadyLost(COPY_ID)
  }

  "CopyDecider.decide" should "mark a registered available copy as damaged" in {
    val command = Command.MarkAsDamaged(COPY_ID)
    val state   = CopyState.empty :+ Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR))

    val actualResult = failOnRaise { underTest.decide(command, state) }

    actualResult should contain only Event.MarkedAsDamaged(COPY_ID)
  }

  it should "make the copy state damaged after applying the MarkedAsDamaged event" in {
    val command = Command.MarkAsDamaged(COPY_ID)
    val state   = CopyState.empty :+ Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR))

    val events       = failOnRaise { underTest.decide(command, state) }
    val updatedState = events.foldLeft(state)(underTest.evolve)

    updatedState.isDamaged(COPY_ID) shouldBe true
  }

  it should "not mark a copy as damaged if it was never registered" in {
    val command = Command.MarkAsDamaged(COPY_ID)
    val state   = CopyState.empty

    val actualResult = interceptRaised { underTest.decide(command, state) }

    actualResult shouldBe Error.CopyNotFound(COPY_ID)
  }

  it should "not mark a copy as damaged if it is already damaged" in {
    val command = Command.MarkAsDamaged(COPY_ID)
    val state   = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.MarkedAsDamaged(COPY_ID)

    val actualResult = interceptRaised { underTest.decide(command, state) }

    actualResult shouldBe Error.AlreadyDamaged(COPY_ID)
  }

  it should "not mark a copy as damaged if it is already lost" in {
    val command = Command.MarkAsDamaged(COPY_ID)
    val state   = CopyState.empty :+
      Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)) :+
      Event.MarkedAsLost(COPY_ID)

    val actualResult = interceptRaised { underTest.decide(command, state) }

    actualResult shouldBe Error.CopyIsLost(COPY_ID)
  }

  "CopyDecider.evolve" should "add the event to the state" in {
    val state = CopyState.empty

    val actualResult =
      underTest.evolve(state, Event.Registered(COPY_ID, COPY_ISBN, TITLE, Seq(AUTHOR)))

    actualResult should contain only Event.Registered(
      COPY_ID,
      COPY_ISBN,
      TITLE,
      Seq(AUTHOR)
    )
  }
}

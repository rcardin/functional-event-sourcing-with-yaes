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

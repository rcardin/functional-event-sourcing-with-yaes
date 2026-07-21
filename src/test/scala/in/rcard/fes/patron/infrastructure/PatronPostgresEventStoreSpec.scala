package in.rcard.fes.patron.infrastructure

import in.rcard.fes.patron.Fixtures.*
import in.rcard.fes.patron.domain.Event
import in.rcard.fes.patron.infrastructure.PatronPostgresEventStore.given
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PatronPostgresEventStoreSpec extends AnyFlatSpec with Matchers {

  "Event codec" should "round-trip Event.Registered through encoding and decoding" in {
    val event: Event = Event.Registered(CARD_ID, PATRON_NAME, BORROW_LIMIT)

    val json    = event.asJson.noSpaces
    val decoded = decode[Event](json)

    decoded shouldBe Right(event)
  }

  it should "round-trip Event.Suspended through encoding and decoding" in {
    val event: Event = Event.Suspended(CARD_ID)

    val json    = event.asJson.noSpaces
    val decoded = decode[Event](json)

    decoded shouldBe Right(event)
  }
}

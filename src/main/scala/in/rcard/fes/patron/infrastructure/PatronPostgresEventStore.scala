package in.rcard.fes.patron.infrastructure

import com.zaxxer.hikari.HikariDataSource
import in.rcard.fes.eventsourcing.EventStorePort
import in.rcard.fes.eventsourcing.EventStorePort.Valuable
import in.rcard.fes.eventsourcing.PostgresJdbcEventStore
import in.rcard.fes.patron.domain.Domain.BorrowLimit
import in.rcard.fes.patron.domain.Domain.PatronId
import in.rcard.fes.patron.domain.Domain.PatronName
import in.rcard.fes.patron.domain.Event
import io.circe.Decoder
import io.circe.Encoder

object PatronPostgresEventStore {

  given Encoder[PatronId] = Encoder.encodeString.contramap(_.value)
  given Decoder[PatronId] = Decoder.decodeString.map(PatronId(_))
  given Encoder[PatronName] = Encoder.encodeString.contramap(_.value)
  given Decoder[PatronName] = Decoder.decodeString.map(PatronName(_))
  given Encoder[BorrowLimit] = Encoder.encodeInt.contramap(_.value)
  given Decoder[BorrowLimit] = Decoder.decodeInt.map(BorrowLimit(_))
  given Encoder[Event] = Encoder.AsObject.derived
  given Decoder[Event] = Decoder.derived

  given patronIdValuable: Valuable[PatronId] with
    def value(patronId: PatronId): String = patronId.value

  given live(using pool: HikariDataSource): EventStorePort[PatronId, Event] =
    PostgresJdbcEventStore[PatronId, Event](pool, "patron")
}

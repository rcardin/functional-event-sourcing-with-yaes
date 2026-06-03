package in.rcard.fes.copy.infrastructure

import cats.Show
import com.zaxxer.hikari.HikariDataSource
import in.rcard.fes.CommandHandler
import in.rcard.fes.EventStorePort
import in.rcard.fes.EventStorePort.Valuable
import in.rcard.fes.PostgresJdbcEventStore
import in.rcard.fes.copy.domain.Command
import in.rcard.fes.copy.domain.Domain.Author
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.fes.copy.domain.Domain.Title
import in.rcard.fes.copy.domain.Error
import in.rcard.fes.copy.domain.Event
import io.circe.Decoder
import io.circe.Encoder

object CopyPostgresEventStore {

  given Encoder[CopyId] = Encoder.encodeString.contramap(_.value)
  given Decoder[CopyId] = Decoder.decodeString.map(CopyId(_))
  given Encoder[ISBN]   = Encoder.encodeString.contramap(_.value)
  given Decoder[ISBN]   = Decoder.decodeString.map(ISBN(_))
  given Encoder[Title]  = Encoder.encodeString.contramap(_.value)
  given Decoder[Title]  = Decoder.decodeString.map(Title(_))
  given Encoder[Author] = Encoder.encodeString.contramap(_.value)
  given Decoder[Author] = Decoder.decodeString.map(Author(_))
  given Encoder[Event]  = Encoder.AsObject.derived
  given Decoder[Event]  = Decoder.derived

  given copyIdValuable: Valuable[CopyId] with
    def value(copyId: CopyId): String = copyId.value

  given live(using pool: HikariDataSource): EventStorePort[CopyId, Event] =
    PostgresJdbcEventStore[CopyId, Event](pool, "copy")
}

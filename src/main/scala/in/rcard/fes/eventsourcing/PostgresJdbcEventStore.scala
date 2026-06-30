package in.rcard.fes.eventsourcing

import com.zaxxer.hikari.HikariDataSource
import in.rcard.yaes.Raise
import in.rcard.yaes.Resource
import in.rcard.yaes.Sync
import in.rcard.yaes.raises
import io.circe.{Decoder, Encoder, parser}
import in.rcard.fes.eventsourcing.EventStorePort.Valuable

import java.sql.SQLException
import scala.annotation.tailrec


class PostgresJdbcEventStore[Id: Valuable, Event: Encoder: Decoder](
    pool: HikariDataSource,
    aggregateType: String
) extends EventStorePort[Id, Event] {

  override def load(
      id: Id
  )(using Sync): EventStorePort.EventStream[Event] raises EventStorePort.Error = {
    val aggregateId = summon[Valuable[Id]].value(id)
    Raise.catching {
      Resource.run {
        val conn = Resource.install(pool.getConnection())(_.close())
        val stmt = Resource.install(
          conn.prepareStatement(
            "SELECT sequence_no, payload FROM events WHERE aggregate_id = ? AND aggregate_type = ? ORDER BY sequence_no"
          )
        )(_.close())
        stmt.setString(1, aggregateId)
        stmt.setString(2, aggregateType)
        val rs = Resource.install(stmt.executeQuery())(_.close())

        var version: Long       = 0L
        var events: List[Event] = Nil

        while rs.next() do {
          version = rs.getLong("sequence_no")
          val payloadStr = rs.getString("payload")
          parser.decode[Event](payloadStr) match {
            case Right(event) => events = events :+ event
            case Left(err)    =>
              Raise.raise(
                EventStorePort.Error.UnexpectedError(s"Failed to decode event: ${err.getMessage}")
              )
          }
        }

        EventStorePort.EventStream(version, events)
      }
    } { e => EventStorePort.Error.UnexpectedError(e.getMessage) }
  }

  override def save(id: Id, expectedVersion: Long, events: Seq[Event])(using
      Sync
  ): Unit raises EventStorePort.Error = {
    if events.isEmpty then return ()
    val aggregateId = summon[Valuable[Id]].value(id)
    Raise.catching {
      Resource.run {
        val conn = Resource.install(pool.getConnection())(_.close())
        val stmt = Resource.install(
          conn.prepareStatement(
            "INSERT INTO events (aggregate_id, aggregate_type, sequence_no, event_type, payload) VALUES (?, ?, ?, ?, ?::jsonb)"
          )
        )(_.close())
        events.zipWithIndex.foreach { case (event, index) =>
          val sequenceNo  = expectedVersion + index + 1
          val eventType   = event.getClass.getSimpleName
          val payloadJson = Encoder[Event].apply(event).noSpaces
          stmt.setString(1, aggregateId)
          stmt.setString(2, aggregateType)
          stmt.setLong(3, sequenceNo)
          stmt.setString(4, eventType)
          stmt.setString(5, payloadJson)
          stmt.addBatch()
        }
        stmt.executeBatch()
      }
    } {
      // executeBatch wraps the failure in a BatchUpdateException whose chained
      // exceptions carry the real SQLState, so the whole chain must be inspected.
      case e: SQLException if isUniqueViolation(e) => EventStorePort.Error.VersionConflict(id)
      case e                                       => EventStorePort.Error.UnexpectedError(e.getMessage)
    }
  }

  // 23505 is the PostgreSQL unique violation error code.
  @tailrec
  private def isUniqueViolation(e: SQLException): Boolean =
    if e.getSQLState == "23505" then true
    else
      e.getNextException match {
        case null => false
        case next => isUniqueViolation(next)
      }
}

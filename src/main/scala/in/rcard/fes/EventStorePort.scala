package in.rcard.fes

import in.rcard.yaes.{Sync, raises}
import in.rcard.fes.EventStorePort.Valuable

trait EventStorePort[Id: Valuable, Event] {
  def load(id: Id)(using Sync): EventStorePort.EventStream[Event] raises EventStorePort.Error
  def save(id: Id, expectedVersion: Long, events: Seq[Event])(using
      Sync
  ): Unit raises EventStorePort.Error
}

object EventStorePort {

  trait Valuable[Id] {
    def value(id: Id): String
  }

  case class EventStream[Event](version: Long, events: Seq[Event])

  enum Error {
    case VersionConflict(id: Any)
    case UnexpectedError(message: String)
  }
}

package in.rcard.fes.eventsourcing

import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.raises

trait CommandHandler[Id, Command, Error, Event] {
  def handle(id: Id, cmd: Command)(using Sync): Seq[Event] raises Error
}

object CommandHandler {
  def apply[Id, Command, Error, Event, State](
      decider: Decider[Command, Event, State, Error],
      eventStore: EventStorePort[Id, Event],
      liftStoreError: EventStorePort.Error => Error
  ): CommandHandler[Id, Command, Error, Event] =
    new CommandHandler[Id, Command, Error, Event] {
      override def handle(id: Id, cmd: Command)(using Sync): Seq[Event] raises Error = {
        def attempt(): Seq[Event] = {
          val EventStorePort.EventStream(version, events) =
            Raise.recover[EventStorePort.Error, EventStorePort.EventStream[Event]] {
              eventStore.load(id)
            } { err => Raise.raise(liftStoreError(err)) }
          val state = events.foldLeft(decider.initialState)(decider.evolve)
          if decider.isTerminal(state) then Seq.empty
          else
            val newEvents = decider.decide(cmd, state)
            Raise.recover[EventStorePort.Error, Seq[Event]] {
              eventStore.save(id, version, newEvents)
              newEvents
            } {
              case EventStorePort.Error.VersionConflict(_) => attempt()
              case other                                   => Raise.raise(liftStoreError(other))
            }
        }
        attempt()
      }
    }
}

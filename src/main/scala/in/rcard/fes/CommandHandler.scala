package in.rcard.fes

import in.rcard.yaes.Raise
import in.rcard.yaes.Sync
import in.rcard.yaes.raises

trait CommandHandler[Id, Command, Error, Event] {
  def handle(id: Id, cmd: Command)(using Sync, Raise[EventStorePort.Error]): Seq[Event] raises Error
}

object CommandHandler {
  def apply[Id, Command, Error, Event, State](
      decider: Decider[Command, Event, State, Error],
      eventStore: EventStorePort[Id, Event]
  ): CommandHandler[Id, Command, Error, Event] =
    new CommandHandler[Id, Command, Error, Event] {
      override def handle(id: Id, cmd: Command)(using
          Sync,
          Raise[EventStorePort.Error]
      ): Seq[Event] raises Error = {
        def attempt(): Seq[Event] = {
          val EventStorePort.EventStream(version, events) = eventStore.load(id)
          val state = events.foldLeft(decider.initialState)(decider.evolve)
          if decider.isTerminal(state) then Seq.empty
          else
            val newEvents = decider.decide(cmd, state)
            Raise.recover[EventStorePort.Error, Seq[Event]] {
              eventStore.save(id, version, newEvents)
              newEvents
            } {
              case EventStorePort.Error.VersionConflict(_) => attempt()
              case other                                   => Raise.raise(other)
            }
        }
        attempt()
      }
    }
}

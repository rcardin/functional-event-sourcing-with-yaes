package in.rcard.fes

import in.rcard.yaes.{Raise, Sync, raises}

trait CommandHandler[Id, Command, Error, Event] {
  def handle(id: Id, cmd: Command)(using Sync, Raise[EventStorePort.Error]): Seq[Event] raises Error
}

object CommandHandler {
  def apply[Id, Command, Error, Event, State](
      decider: Decider[Command, Event, State, Error],
      eventStore: EventStorePort[Id, Event]
  ): CommandHandler[Id, Command, Error, Event] =
    new CommandHandler[Id, Command, Error, Event] {
      override def handle(id: Id, cmd: Command)(using Sync, Raise[EventStorePort.Error]): Seq[Event] raises Error = {
        def attempt(): Seq[Event] = {
          val stream = eventStore.load(id)
          val state  = stream.events.foldLeft(decider.initialState)(decider.evolve)
          if decider.isTerminal(state) then Seq.empty
          else
            val newEvents = decider.decide(cmd, state)
            Raise.recover[EventStorePort.Error, Seq[Event]] {
              eventStore.save(id, stream.version, newEvents)
              newEvents
            } {
              case EventStorePort.Error.VersionConflict(_) => attempt()
              case other                                    => Raise.raise(other)
            }
        }
        attempt()
      }
    }

  // TODO: replace with real DI wiring once EventStorePort is implemented
  given [Id, Command, Error, Event]: CommandHandler[Id, Command, Error, Event] =
    new CommandHandler[Id, Command, Error, Event] {
      override def handle(id: Id, cmd: Command)(using Sync, Raise[EventStorePort.Error]): Seq[Event] raises Error =
        Seq.empty
    }
}

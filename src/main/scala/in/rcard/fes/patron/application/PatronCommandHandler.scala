package in.rcard.fes.patron.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.eventsourcing.EventStorePort
import in.rcard.fes.patron.domain.PatronDecider
import in.rcard.fes.patron.domain.Domain.PatronId
import in.rcard.fes.patron.domain.{Command, Error, Event}

object PatronCommandHandler {

  given live(using
      decider: PatronDecider,
      eventStore: EventStorePort[PatronId, Event]
  ): CommandHandler[PatronId, Command, Error, Event] =
    CommandHandler(
      decider,
      eventStore,
      {
        case EventStorePort.Error.UnexpectedError(msg) => Error.UnexpectedError(msg)
        case EventStorePort.Error.VersionConflict(id)  =>
          Error.UnexpectedError(s"Version conflict for patron: $id")
      },
      // Unreachable while PatronDecider.isTerminal is always false.
      (id, _) => Error.UnexpectedError(s"Unexpected terminal state for patron: $id")
    )
}

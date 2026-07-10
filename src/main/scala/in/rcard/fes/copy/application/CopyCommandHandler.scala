package in.rcard.fes.copy.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.eventsourcing.EventStorePort
import in.rcard.fes.copy.domain.CopyDecider
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.{Command, Error, Event}

object CopyCommandHandler {

  given live(using
      decider: CopyDecider,
      eventStore: EventStorePort[CopyId, Event]
  ): CommandHandler[CopyId, Command, Error, Event] =
    CommandHandler(
      decider,
      eventStore,
      {
        case EventStorePort.Error.UnexpectedError(msg) => Error.UnexpectedError(msg)
        case EventStorePort.Error.VersionConflict(id)  => Error.UnexpectedError(s"Version conflict for copy: $id")
      },
      (id, _) => Error.CopyIsRemoved(id)
    )
}

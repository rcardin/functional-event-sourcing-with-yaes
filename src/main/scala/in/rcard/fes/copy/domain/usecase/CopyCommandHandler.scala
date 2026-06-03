package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.CommandHandler
import in.rcard.fes.EventStorePort
import in.rcard.fes.copy.domain.Domain.{CopyId}
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises, Sync}

object CopyCommandHandler {

  given live(using
      decider: CopyDecider,
      eventStore: EventStorePort[CopyId, Event]
  ): CommandHandler[CopyId, Command, Error, Event] =
    CommandHandler(decider, eventStore)
}

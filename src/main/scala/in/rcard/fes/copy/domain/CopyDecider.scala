package in.rcard.fes.copy.domain

import in.rcard.fes.eventsourcing.Decider
import in.rcard.fes.copy.domain.Domain.{CopyState, isLost, isRegistered}
import in.rcard.fes.copy.domain.Event.{MarkedAsLost, Registered}
import in.rcard.yaes.{Raise, raises}

class CopyDecider extends Decider[Command, Event, CopyState, Error] {

  override def decide(command: Command, state: CopyState): Seq[Event] raises Error = command match {
    case registration: Command.Register => registerCopy(registration, state)
    case markAsLost: Command.MarkAsLost => markCopyAsLost(markAsLost, state)
  }

  private def registerCopy(command: Command.Register, state: CopyState): Seq[Event] raises Error = {
    if (state.isRegistered(command.id))
      Raise.raise(Error.AlreadyRegistered(command.id))
    else Seq(Registered(command.id, command.isbn, command.title, command.authors))
  }

  private def markCopyAsLost(command: Command.MarkAsLost, state: CopyState): Seq[Event] raises Error = {
    if (!state.isRegistered(command.id))
      Raise.raise(Error.CopyNotFound(command.id))
    else if (state.isLost(command.id))
      Raise.raise(Error.AlreadyLost(command.id))
    else Seq(MarkedAsLost(command.id))
  }

  override def evolve(state: CopyState, event: Event): CopyState = state :+ event

  override val initialState: CopyState = CopyState.empty

  override def isTerminal(state: CopyState): Boolean = false
}

object CopyDecider {
  given live: CopyDecider = new CopyDecider
}

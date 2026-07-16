package in.rcard.fes.patron.domain

import in.rcard.fes.eventsourcing.Decider
import in.rcard.fes.patron.domain.Domain.{PatronState, isRegistered}
import in.rcard.fes.patron.domain.Event.Registered
import in.rcard.yaes.{Raise, raises}

class PatronDecider extends Decider[Command, Event, PatronState, Error] {

  override def decide(command: Command, state: PatronState): Seq[Event] raises Error = command match {
    case registration: Command.Register => registerPatron(registration, state)
  }

  private def registerPatron(command: Command.Register, state: PatronState): Seq[Event] raises Error = {
    if (state.isRegistered(command.id))
      Raise.raise(Error.AlreadyRegistered(command.id))
    else Seq(Registered(command.id, command.name, command.borrowLimit))
  }

  override def evolve(state: PatronState, event: Event): PatronState = state :+ event

  override val initialState: PatronState = PatronState.empty

  override def isTerminal(state: PatronState): Boolean = false
}

object PatronDecider {
  given live: PatronDecider = new PatronDecider
}

package in.rcard.fes.patron.domain

import in.rcard.fes.eventsourcing.Decider
import in.rcard.fes.patron.domain.Domain.{PatronState, isRegistered, isSuspended}
import in.rcard.fes.patron.domain.Event.{Registered, Reinstated, Suspended}
import in.rcard.yaes.{Raise, raises}

class PatronDecider extends Decider[Command, Event, PatronState, Error] {

  override def decide(command: Command, state: PatronState): Seq[Event] raises Error = command match {
    case registration: Command.Register => registerPatron(registration, state)
    case suspend: Command.Suspend       => suspendPatron(suspend, state)
    case reinstate: Command.Reinstate   => reinstatePatron(reinstate, state)
  }

  private def registerPatron(command: Command.Register, state: PatronState): Seq[Event] raises Error = {
    if (state.isRegistered(command.id))
      Raise.raise(Error.AlreadyRegistered(command.id))
    else Seq(Registered(command.id, command.name, command.borrowLimit))
  }

  private def suspendPatron(command: Command.Suspend, state: PatronState): Seq[Event] raises Error = {
    if (!state.isRegistered(command.id))
      Raise.raise(Error.PatronNotFound(command.id))
    else if (state.isSuspended(command.id))
      Raise.raise(Error.AlreadySuspended(command.id))
    else Seq(Suspended(command.id))
  }

  private def reinstatePatron(command: Command.Reinstate, state: PatronState): Seq[Event] raises Error = {
    if (!state.isRegistered(command.id))
      Raise.raise(Error.PatronNotFound(command.id))
    else if (!state.isSuspended(command.id))
      Raise.raise(Error.NotSuspended(command.id))
    else Seq(Reinstated(command.id))
  }

  override def evolve(state: PatronState, event: Event): PatronState = state :+ event

  override val initialState: PatronState = PatronState.empty

  override def isTerminal(state: PatronState): Boolean = false
}

object PatronDecider {
  given live: PatronDecider = new PatronDecider
}

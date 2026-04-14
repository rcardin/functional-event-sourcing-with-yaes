package in.rcard.fes.copy

import in.rcard.fes.Decider
import in.rcard.fes.copy.Domain.{CopyState, isRegistered}
import in.rcard.fes.copy.Event.Registered
import in.rcard.yaes.{Raise, raises}

class CopyDecider extends Decider[Command, Event, CopyState, Error] {

  override def decide(command: Command, state: CopyState): Seq[Event] raises Error = command match {
    case registration: Command.Register => registerCopy(registration, state)

  }

  private def registerCopy(command: Command.Register, state: CopyState): Seq[Event] raises Error = {
    if (state.isRegistered(command.id))
      Raise.raise(Error.AlreadyRegistered(command.id))
    else Seq(Registered(command.id, command.isbn, command.title, command.author))
  }

  override def evolve(state: CopyState, event: Event): CopyState = state :+ event

  override val initialState: CopyState = CopyState.empty

  override def isTerminal(state: CopyState): Boolean = ???
}

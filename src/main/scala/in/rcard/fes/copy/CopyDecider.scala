package in.rcard.fes.copy

import in.rcard.fes.Decider
import in.rcard.fes.copy.Domain.CopyState
import in.rcard.yaes.raises

class CopyDecider extends Decider[Command, Event, CopyState, Error] {

  override def decide(command: Command, state: CopyState): CopyState raises Error = ???

  override def evolve(state: CopyState, event: Event): CopyState = ???

  override val initialState: CopyState = ???

  override def isTerminal(state: CopyState): Boolean = ???
}

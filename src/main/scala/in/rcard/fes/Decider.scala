package in.rcard.fes

import in.rcard.yaes.raises

trait Decider[Command, Event, State, Error] {
  def decide(command: Command, state: State): Seq[Event] raises Error
  def evolve(state: State, event: Event): State
  val initialState: State
  def isTerminal(state: State): Boolean
}

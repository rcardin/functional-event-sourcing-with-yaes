package in.rcard.fes.copy.domain

import in.rcard.fes.eventsourcing.Decider
import in.rcard.fes.copy.domain.Domain.{CopyState, isDamaged, isLost, isRegistered}
import in.rcard.fes.copy.domain.Event.{MarkedAsDamaged, MarkedAsLost, Registered, Removed, Repaired}
import in.rcard.yaes.{Raise, raises}

class CopyDecider extends Decider[Command, Event, CopyState, Error] {

  override def decide(command: Command, state: CopyState): Seq[Event] raises Error = command match {
    case registration: Command.Register       => registerCopy(registration, state)
    case markAsLost: Command.MarkAsLost       => markCopyAsLost(markAsLost, state)
    case markAsDamaged: Command.MarkAsDamaged => markCopyAsDamaged(markAsDamaged, state)
    case repair: Command.Repair               => repairCopy(repair, state)
    case remove: Command.Remove               => removeCopy(remove, state)
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

  private def markCopyAsDamaged(command: Command.MarkAsDamaged, state: CopyState): Seq[Event] raises Error = {
    if (!state.isRegistered(command.id))
      Raise.raise(Error.CopyNotFound(command.id))
    else if (state.isLost(command.id))
      Raise.raise(Error.CopyIsLost(command.id))
    else if (state.isDamaged(command.id))
      Raise.raise(Error.AlreadyDamaged(command.id))
    else Seq(MarkedAsDamaged(command.id))
  }

  private def repairCopy(command: Command.Repair, state: CopyState): Seq[Event] raises Error = {
    if (!state.isRegistered(command.id))
      Raise.raise(Error.CopyNotFound(command.id))
    else if (!state.isDamaged(command.id))
      Raise.raise(Error.NotDamaged(command.id))
    else Seq(Repaired(command.id))
  }

  private def removeCopy(command: Command.Remove, state: CopyState): Seq[Event] raises Error = {
    if (!state.isRegistered(command.id))
      Raise.raise(Error.CopyNotFound(command.id))
    else Seq(Removed(command.id))
  }

  override def evolve(state: CopyState, event: Event): CopyState = state :+ event

  override val initialState: CopyState = CopyState.empty

  override def isTerminal(state: CopyState): Boolean = state.lastOption match {
    case Some(Event.Removed(_)) => true
    case _                      => false
  }
}

object CopyDecider {
  given live: CopyDecider = new CopyDecider
}

package in.rcard.fes.patron.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.patron.domain.Domain.PatronId
import in.rcard.fes.patron.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Sync

trait ReinstatePatronUseCase {
  def reinstate(id: PatronId)(using Sync): Unit raises ReinstatePatronError
}
object ReinstatePatronUseCase {

  def apply(
      commandHandler: CommandHandler[PatronId, Command, Error, Event]
  ): ReinstatePatronUseCase = new ReinstatePatronUseCase {

    override def reinstate(id: PatronId)(using Sync): Unit raises ReinstatePatronError = {
      val events = Raise.recover[Error, Seq[Event]] {
        commandHandler.handle(id, Command.Reinstate(id))
      } {
        case Error.PatronNotFound(patronId) =>
          Raise.raise(ReinstatePatronError.PatronNotFound(patronId))
        case Error.NotSuspended(patronId) =>
          Raise.raise(ReinstatePatronError.NotSuspended(patronId))
        case Error.AlreadyRegistered(_) | Error.AlreadySuspended(_) =>
          Raise.raise(ReinstatePatronError.UnexpectedError("Unexpected state while reinstating a patron"))
        case Error.UnexpectedError(msg) =>
          Raise.raise(ReinstatePatronError.UnexpectedError(msg))
      }
      events match {
        case Seq(Event.Reinstated(_)) => ()
        case _ =>
          Raise.raise(ReinstatePatronError.UnexpectedError("Unexpected state after reinstating a patron"))
      }
    }
  }

  given live(using
      commandHandler: CommandHandler[PatronId, Command, Error, Event]
  ): ReinstatePatronUseCase =
    ReinstatePatronUseCase(commandHandler)
}

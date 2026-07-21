package in.rcard.fes.patron.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.patron.domain.Domain.PatronId
import in.rcard.fes.patron.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Sync

trait SuspendPatronUseCase {
  def suspend(id: PatronId)(using Sync): Unit raises SuspendPatronError
}
object SuspendPatronUseCase {

  def apply(
      commandHandler: CommandHandler[PatronId, Command, Error, Event]
  ): SuspendPatronUseCase = new SuspendPatronUseCase {

    override def suspend(id: PatronId)(using Sync): Unit raises SuspendPatronError = {
      val events = Raise.recover[Error, Seq[Event]] {
        commandHandler.handle(id, Command.Suspend(id))
      } {
        case Error.PatronNotFound(patronId) =>
          Raise.raise(SuspendPatronError.PatronNotFound(patronId))
        case Error.AlreadySuspended(patronId) =>
          Raise.raise(SuspendPatronError.AlreadySuspended(patronId))
        case Error.AlreadyRegistered(_) =>
          Raise.raise(SuspendPatronError.UnexpectedError("Unexpected state while suspending a patron"))
        case Error.UnexpectedError(msg) =>
          Raise.raise(SuspendPatronError.UnexpectedError(msg))
      }
      events match {
        case Event.Suspended(_) :: Nil => ()
        case _ =>
          Raise.raise(SuspendPatronError.UnexpectedError("Unexpected state after suspending a patron"))
      }
    }
  }

  given live(using
      commandHandler: CommandHandler[PatronId, Command, Error, Event]
  ): SuspendPatronUseCase =
    SuspendPatronUseCase(commandHandler)
}

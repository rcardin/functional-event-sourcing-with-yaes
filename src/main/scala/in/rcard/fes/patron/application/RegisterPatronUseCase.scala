package in.rcard.fes.patron.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.patron.domain.Domain.{BorrowLimit, PatronId, PatronName}
import in.rcard.fes.patron.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Sync

trait RegisterPatronUseCase {
  def registerPatron(cardId: PatronId, name: PatronName, borrowLimit: BorrowLimit)(using
      Sync
  ): PatronId raises RegisterPatronError
}
object RegisterPatronUseCase {

  def apply(
      commandHandler: CommandHandler[PatronId, Command, Error, Event]
  ): RegisterPatronUseCase = new RegisterPatronUseCase {

    override def registerPatron(cardId: PatronId, name: PatronName, borrowLimit: BorrowLimit)(using
        Sync
    ): PatronId raises RegisterPatronError = {
      val cmd    = Command.Register(cardId, name, borrowLimit)
      val events = Raise.recover[Error, Seq[Event]] {
        commandHandler.handle(cardId, cmd)
      } {
        case Error.AlreadyRegistered(id) =>
          Raise.raise(RegisterPatronError.AlreadyRegistered(id))
        case Error.UnexpectedError(msg) =>
          Raise.raise(RegisterPatronError.UnexpectedError(msg))
      }
      events match {
        case Event.Registered(patronId, _, _) :: Nil => patronId
        case _ => Raise.raise(RegisterPatronError.UnexpectedError("Unexpected state after patron registration"))
      }
    }
  }

  given live(using
      commandHandler: CommandHandler[PatronId, Command, Error, Event]
  ): RegisterPatronUseCase =
    RegisterPatronUseCase(commandHandler)
}

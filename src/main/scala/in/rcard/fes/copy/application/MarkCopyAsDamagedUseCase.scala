package in.rcard.fes.copy.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Sync

trait MarkCopyAsDamagedUseCase {
  def markAsDamaged(id: CopyId)(using Sync): Unit raises MarkCopyAsDamagedError
}
object MarkCopyAsDamagedUseCase {

  def apply(
      commandHandler: CommandHandler[CopyId, Command, Error, Event]
  ): MarkCopyAsDamagedUseCase = new MarkCopyAsDamagedUseCase {

    override def markAsDamaged(id: CopyId)(using Sync): Unit raises MarkCopyAsDamagedError = {
      val events = Raise.recover[Error, Seq[Event]] {
        commandHandler.handle(id, Command.MarkAsDamaged(id))
      } {
        case Error.CopyNotFound(copyId) =>
          Raise.raise(MarkCopyAsDamagedError.CopyNotFound(copyId))
        case Error.AlreadyDamaged(copyId) =>
          Raise.raise(MarkCopyAsDamagedError.AlreadyDamaged(copyId))
        case Error.CopyIsLost(copyId) =>
          Raise.raise(MarkCopyAsDamagedError.CopyIsLost(copyId))
        case Error.AlreadyRegistered(_) | Error.AlreadyLost(_) | Error.NotDamaged(_) =>
          Raise.raise(MarkCopyAsDamagedError.UnexpectedError("Unexpected state while marking a copy as damaged"))
        case Error.UnexpectedError(msg) =>
          Raise.raise(MarkCopyAsDamagedError.UnexpectedError(msg))
      }
      events match {
        case Seq(Event.MarkedAsDamaged(_)) => ()
        case _ =>
          Raise.raise(MarkCopyAsDamagedError.UnexpectedError("Unexpected state after marking a copy as damaged"))
      }
    }
  }

  given live(using
      commandHandler: CommandHandler[CopyId, Command, Error, Event]
  ): MarkCopyAsDamagedUseCase =
    MarkCopyAsDamagedUseCase(commandHandler)
}

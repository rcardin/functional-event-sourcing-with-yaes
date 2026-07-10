package in.rcard.fes.copy.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Sync

trait RemoveCopyUseCase {
  def remove(id: CopyId)(using Sync): Unit raises RemoveCopyError
}
object RemoveCopyUseCase {

  def apply(
      commandHandler: CommandHandler[CopyId, Command, Error, Event]
  ): RemoveCopyUseCase = new RemoveCopyUseCase {

    override def remove(id: CopyId)(using Sync): Unit raises RemoveCopyError = {
      val events = Raise.recover[Error, Seq[Event]] {
        commandHandler.handle(id, Command.Remove(id))
      } {
        case Error.CopyNotFound(copyId) =>
          Raise.raise(RemoveCopyError.CopyNotFound(copyId))
        case Error.CopyIsRemoved(copyId) =>
          Raise.raise(RemoveCopyError.CopyIsRemoved(copyId))
        case Error.AlreadyRegistered(_) | Error.AlreadyLost(_) | Error.AlreadyDamaged(_) | Error.CopyIsLost(_) |
            Error.NotDamaged(_) =>
          Raise.raise(RemoveCopyError.UnexpectedError("Unexpected state while removing a copy"))
        case Error.UnexpectedError(msg) =>
          Raise.raise(RemoveCopyError.UnexpectedError(msg))
      }
      events match {
        case Seq(Event.Removed(_)) => ()
        case _ =>
          Raise.raise(RemoveCopyError.UnexpectedError("Unexpected state after removing a copy"))
      }
    }
  }

  given live(using
      commandHandler: CommandHandler[CopyId, Command, Error, Event]
  ): RemoveCopyUseCase =
    RemoveCopyUseCase(commandHandler)
}

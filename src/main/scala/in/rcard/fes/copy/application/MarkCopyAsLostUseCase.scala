package in.rcard.fes.copy.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Sync

trait MarkCopyAsLostUseCase {
  def markAsLost(id: CopyId)(using Sync): Unit raises MarkCopyAsLostError
}
object MarkCopyAsLostUseCase {

  def apply(
      commandHandler: CommandHandler[CopyId, Command, Error, Event]
  ): MarkCopyAsLostUseCase = new MarkCopyAsLostUseCase {

    override def markAsLost(id: CopyId)(using Sync): Unit raises MarkCopyAsLostError = {
      val events = Raise.recover[Error, Seq[Event]] {
        commandHandler.handle(id, Command.MarkAsLost(id))
      } {
        case Error.CopyNotFound(copyId) =>
          Raise.raise(MarkCopyAsLostError.CopyNotFound(copyId))
        case Error.AlreadyLost(copyId) =>
          Raise.raise(MarkCopyAsLostError.AlreadyLost(copyId))
        case Error.AlreadyRegistered(_) | Error.AlreadyDamaged(_) | Error.CopyIsLost(_) | Error.NotDamaged(_) |
            Error.CopyIsRemoved(_) =>
          Raise.raise(MarkCopyAsLostError.UnexpectedError("Unexpected state while marking a copy as lost"))
        case Error.UnexpectedError(msg) =>
          Raise.raise(MarkCopyAsLostError.UnexpectedError(msg))
      }
      events match {
        case Event.MarkedAsLost(_) :: Nil => ()
        case _ =>
          Raise.raise(MarkCopyAsLostError.UnexpectedError("Unexpected state after marking a copy as lost"))
      }
    }
  }

  given live(using
      commandHandler: CommandHandler[CopyId, Command, Error, Event]
  ): MarkCopyAsLostUseCase =
    MarkCopyAsLostUseCase(commandHandler)
}

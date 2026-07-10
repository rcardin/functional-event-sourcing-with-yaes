package in.rcard.fes.copy.application

import in.rcard.fes.eventsourcing.CommandHandler
import in.rcard.fes.copy.domain.Domain.CopyId
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Sync

trait RepairCopyUseCase {
  def repair(id: CopyId)(using Sync): Unit raises RepairCopyError
}
object RepairCopyUseCase {

  def apply(
      commandHandler: CommandHandler[CopyId, Command, Error, Event]
  ): RepairCopyUseCase = new RepairCopyUseCase {

    override def repair(id: CopyId)(using Sync): Unit raises RepairCopyError = {
      val events = Raise.recover[Error, Seq[Event]] {
        commandHandler.handle(id, Command.Repair(id))
      } {
        case Error.CopyNotFound(copyId) =>
          Raise.raise(RepairCopyError.CopyNotFound(copyId))
        case Error.NotDamaged(copyId) =>
          Raise.raise(RepairCopyError.NotDamaged(copyId))
        case Error.AlreadyRegistered(_) | Error.AlreadyLost(_) | Error.AlreadyDamaged(_) | Error.CopyIsLost(_) |
            Error.CopyIsRemoved(_) =>
          Raise.raise(RepairCopyError.UnexpectedError("Unexpected state while repairing a copy"))
        case Error.UnexpectedError(msg) =>
          Raise.raise(RepairCopyError.UnexpectedError(msg))
      }
      events match {
        case Seq(Event.Repaired(_)) => ()
        case _ =>
          Raise.raise(RepairCopyError.UnexpectedError("Unexpected state after repairing a copy"))
      }
    }
  }

  given live(using
      commandHandler: CommandHandler[CopyId, Command, Error, Event]
  ): RepairCopyUseCase =
    RepairCopyUseCase(commandHandler)
}

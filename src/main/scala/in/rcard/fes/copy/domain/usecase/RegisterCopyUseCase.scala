package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.{CommandHandler, EventStorePort}
import in.rcard.fes.copy.domain.Domain.{CopyId, ISBN}
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.CopyToRegister
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Random
import in.rcard.yaes.Sync

trait RegisterCopyUseCase {
  def registerCopy(isbn: ISBN)(using Random, Sync): CopyId raises Error
}
object RegisterCopyUseCase {

  def apply(
      copyIdGenerator: CopyIdGenerator,
      commandHandler: CommandHandler[CopyId, Command, Error, Event],
      findCopyByIsbnPort: FindCopyByIsbnPort
  ): RegisterCopyUseCase = new RegisterCopyUseCase {

    def findCopyFromCatalog(isbn: ISBN)(using Sync): CopyToRegister raises Error = {
      Raise.recover {
        findCopyByIsbnPort.find(isbn)
      } {
        case FindCopyByIsbnPort.Error.NotFound(isbn) =>
          Raise.raise(Error.UnexpectedError(s"ISBN not found in catalog: ${isbn.value}"))
        case FindCopyByIsbnPort.Error.UnexpectedError(msg) =>
          Raise.raise(Error.UnexpectedError(msg))
      }
    }

    override def registerCopy(isbn: ISBN)(using Random, Sync): CopyId raises Error = {
      val catalogCopy = findCopyFromCatalog(isbn)
      val newCopyId   = copyIdGenerator.generate()
      val cmd         = Command.Register(
        newCopyId,
        catalogCopy.isbn,
        catalogCopy.title,
        catalogCopy.authors
      )
      val events = Raise.recover[EventStorePort.Error, Seq[Event]] {
        commandHandler.handle(newCopyId, cmd)
      } {
        case EventStorePort.Error.VersionConflict(id) =>
          Raise.raise(Error.UnexpectedError(s"Version conflict for copy: $id"))
        case EventStorePort.Error.UnexpectedError(msg) =>
          Raise.raise(Error.UnexpectedError(msg))
      }
      events match {
        case Event.Registered(copyId, _, _, _) :: Nil => copyId
        case _ => Raise.raise(Error.UnexpectedError("Unexpected state after copy registration"))
      }
    }
  }

  given live(using
      copyIdGenerator: CopyIdGenerator,
      commandHandler: CommandHandler[CopyId, Command, Error, Event],
      findCopyByIsbnPort: FindCopyByIsbnPort
  ): RegisterCopyUseCase =
    RegisterCopyUseCase(copyIdGenerator, commandHandler, findCopyByIsbnPort)
}

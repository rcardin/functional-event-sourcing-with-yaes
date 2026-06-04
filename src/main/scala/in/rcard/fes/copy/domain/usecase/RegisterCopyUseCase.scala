package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.CommandHandler
import in.rcard.fes.copy.domain.Domain.{CopyId, ISBN}
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.CopyToRegister
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.yaes.{Raise, raises}
import in.rcard.yaes.Random
import in.rcard.yaes.Sync

trait RegisterCopyUseCase {
  def registerCopy(isbn: ISBN)(using Random, Sync): CopyId raises RegisterCopyError
}
object RegisterCopyUseCase {

  def apply(
      copyIdGenerator: CopyIdGenerator,
      commandHandler: CommandHandler[CopyId, Command, Error, Event],
      findCopyByIsbnPort: FindCopyByIsbnPort
  ): RegisterCopyUseCase = new RegisterCopyUseCase {

    def findCopyFromCatalog(isbn: ISBN)(using Sync): CopyToRegister raises RegisterCopyError = {
      Raise.recover {
        findCopyByIsbnPort.find(isbn)
      } {
        case FindCopyByIsbnPort.Error.NotFound(isbn) =>
          Raise.raise(RegisterCopyError.CopyNotFoundInCatalog(isbn))
        case FindCopyByIsbnPort.Error.UnexpectedError(msg) =>
          Raise.raise(RegisterCopyError.UnexpectedError(msg))
      }
    }

    override def registerCopy(isbn: ISBN)(using Random, Sync): CopyId raises RegisterCopyError = {
      val catalogCopy = findCopyFromCatalog(isbn)
      val newCopyId   = copyIdGenerator.generate()
      val cmd         = Command.Register(
        newCopyId,
        catalogCopy.isbn,
        catalogCopy.title,
        catalogCopy.authors
      )
      val events = Raise.recover[Error, Seq[Event]] {
        commandHandler.handle(newCopyId, cmd)
      } {
        case Error.AlreadyRegistered(id) =>
          Raise.raise(RegisterCopyError.AlreadyRegistered(id))
        case Error.UnexpectedError(msg) =>
          Raise.raise(RegisterCopyError.UnexpectedError(msg))
      }
      events match {
        case Event.Registered(copyId, _, _, _) :: Nil => copyId
        case _ => Raise.raise(RegisterCopyError.UnexpectedError("Unexpected state after copy registration"))
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

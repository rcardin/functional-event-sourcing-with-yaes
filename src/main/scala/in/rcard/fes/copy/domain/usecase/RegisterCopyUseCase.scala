package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.CommandHandler
import in.rcard.fes.copy.domain
import in.rcard.fes.copy.domain.Domain.{Author, CopyId, ISBN, Title}
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase.CopyToRegister
import in.rcard.fes.copy.domain.{Command, Error, Event}
import in.rcard.fes.utils.reader
import in.rcard.yaes.Reader.read
import in.rcard.yaes.{Raise, Reader, raises, reads}

trait RegisterCopyUseCase {
  def registerCopy(copyToRegister: CopyToRegister): CopyId raises Error
}
object RegisterCopyUseCase {
  case class CopyToRegister(isbn: ISBN, title: Title, author: Author)

  def apply(
      copyIdGenerator: CopyIdGenerator,
      commandHandler: CommandHandler[CopyId, Command, Error, Event]
  ): RegisterCopyUseCase = new RegisterCopyUseCase {
    override def registerCopy(copyToRegister: CopyToRegister): CopyId raises Error = {
      val newCopyId = copyIdGenerator.generate()
      val cmd       = Command.Register(
        newCopyId,
        copyToRegister.isbn,
        copyToRegister.title,
        copyToRegister.author
      )
      val events = commandHandler.handle(newCopyId, cmd)
      events match {
        case Event.Registered(copyId, _, _, _) :: Nil => copyId
        case _ => Raise.raise(Error.UnexpectedError("Unexpected state after copy registration"))
      }
    }
  }

  given live: Reader[RegisterCopyUseCase] reads CopyIdGenerator reads FindCopyByIsbnPort reads
    CommandHandler[CopyId, Command, Error, Event] =
    reader(
      RegisterCopyUseCase(
        read[CopyIdGenerator],
        read[CommandHandler[CopyId, Command, Error, Event]]
      )
    )
}

trait CopyIdGenerator {
  def generate(): CopyId
}
object CopyIdGenerator {

  def apply(): CopyIdGenerator = () => CopyId("1")

  given Reader[CopyIdGenerator] = reader(CopyIdGenerator())
}

package in.rcard.fes.copy.domain.port

import in.rcard.fes.copy.domain.Domain.{Author, ISBN, Title}
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.{CopyToRegister, Error}
import in.rcard.yaes.raises

trait FindCopyByIsbnPort {
  def find(isbn: ISBN): CopyToRegister raises Error
}
object FindCopyByIsbnPort {

  case class CopyToRegister(isbn: ISBN, title: Title, authors: Seq[Author])

  enum Error {
    case NotFound(isbn: ISBN)
    case UnexpectedError(message: String)
  }
}

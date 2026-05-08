package in.rcard.fes.copy.domain.port

import in.rcard.fes.copy.domain.Domain
import in.rcard.fes.copy.domain.Domain.ISBN
import in.rcard.fes.copy.domain.port.FindCopyByIsbnPort.Error
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase.CopyToRegister
import in.rcard.yaes.Reader.read
import in.rcard.yaes.Reader.reader
import in.rcard.yaes.http.client.{HttpRequest, Uri, YaesClient}
import in.rcard.yaes.{Reader, raises, reads}

trait FindCopyByIsbnPort {
  // FIXME Move the CopuToRegister here
  def find(isbn: ISBN): CopyToRegister raises Error
}
object FindCopyByIsbnPort {

  enum Error {
    case NotFound(isbn: ISBN)
    case UnexpectedError(message: String)
  }
}

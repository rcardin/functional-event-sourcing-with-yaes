package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.copy.domain.Domain.{Author, CopyId, ISBN, Title}
import in.rcard.fes.copy.domain.usecase.RegisterCopyUseCase.CopyToRegister

trait RegisterCopyUseCase {
  def registerCopy(copyToRegister: CopyToRegister): CopyId
}
object RegisterCopyUseCase {
  case class CopyToRegister(isbn: ISBN, title: Title, author: Author)
}

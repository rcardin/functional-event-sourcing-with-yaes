package in.rcard.fes.copy.domain.usecase

import in.rcard.fes.copy.domain.Domain.{CopyId, ISBN}

enum RegisterCopyError {
  case CopyNotFoundInCatalog(isbn: ISBN)
  case AlreadyRegistered(id: CopyId)
  case UnexpectedError(message: String)
}

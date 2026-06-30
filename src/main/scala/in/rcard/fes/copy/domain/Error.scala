package in.rcard.fes.copy.domain

import Domain.CopyId

enum Error {
  case AlreadyRegistered(id: CopyId)
  case CopyNotFound(id: CopyId)
  case AlreadyLost(id: CopyId)
  case UnexpectedError(message: String)
}

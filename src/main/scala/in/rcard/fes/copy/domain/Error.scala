package in.rcard.fes.copy.domain

import Domain.CopyId

enum Error {
  case AlreadyRegistered(id: CopyId)
  case CopyNotFound(id: CopyId)
  case AlreadyLost(id: CopyId)
  case AlreadyDamaged(id: CopyId)
  case CopyIsLost(id: CopyId)
  case NotDamaged(id: CopyId)
  case CopyIsRemoved(id: CopyId)
  case UnexpectedError(message: String)
}

package in.rcard.fes.copy.application

import in.rcard.fes.copy.domain.Domain.CopyId

enum MarkCopyAsDamagedError {
  case CopyNotFound(id: CopyId)
  case AlreadyDamaged(id: CopyId)
  case CopyIsLost(id: CopyId)
  case UnexpectedError(message: String)
}

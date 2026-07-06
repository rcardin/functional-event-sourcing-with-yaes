package in.rcard.fes.copy.application

import in.rcard.fes.copy.domain.Domain.CopyId

enum RepairCopyError {
  case CopyNotFound(id: CopyId)
  case NotDamaged(id: CopyId)
  case UnexpectedError(message: String)
}

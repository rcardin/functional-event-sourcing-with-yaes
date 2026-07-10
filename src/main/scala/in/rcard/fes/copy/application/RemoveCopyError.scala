package in.rcard.fes.copy.application

import in.rcard.fes.copy.domain.Domain.CopyId

enum RemoveCopyError {
  case CopyNotFound(id: CopyId)
  case CopyIsRemoved(id: CopyId)
  case UnexpectedError(message: String)
}

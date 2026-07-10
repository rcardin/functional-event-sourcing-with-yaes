package in.rcard.fes.copy.application

import in.rcard.fes.copy.domain.Domain.CopyId

enum MarkCopyAsLostError {
  case CopyNotFound(id: CopyId)
  case AlreadyLost(id: CopyId)
  case CopyIsRemoved(id: CopyId)
  case UnexpectedError(message: String)
}

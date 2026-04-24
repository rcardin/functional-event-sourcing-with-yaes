package in.rcard.fes.copy.domain

import Domain.CopyId

enum Error {
  case AlreadyRegistered(id: CopyId)
  case UnexpectedError(message: String)
}

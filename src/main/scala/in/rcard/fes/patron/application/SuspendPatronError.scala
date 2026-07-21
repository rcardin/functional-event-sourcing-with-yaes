package in.rcard.fes.patron.application

import in.rcard.fes.patron.domain.Domain.PatronId

enum SuspendPatronError {
  case PatronNotFound(id: PatronId)
  case AlreadySuspended(id: PatronId)
  case UnexpectedError(message: String)
}

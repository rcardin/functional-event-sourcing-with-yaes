package in.rcard.fes.patron.application

import in.rcard.fes.patron.domain.Domain.PatronId

enum ReinstatePatronError {
  case PatronNotFound(id: PatronId)
  case NotSuspended(id: PatronId)
  case UnexpectedError(message: String)
}

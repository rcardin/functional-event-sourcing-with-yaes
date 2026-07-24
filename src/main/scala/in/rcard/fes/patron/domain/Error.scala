package in.rcard.fes.patron.domain

import Domain.PatronId

enum Error {
  case AlreadyRegistered(id: PatronId)
  case PatronNotFound(id: PatronId)
  case AlreadySuspended(id: PatronId)
  case NotSuspended(id: PatronId)
  case UnexpectedError(message: String)
}

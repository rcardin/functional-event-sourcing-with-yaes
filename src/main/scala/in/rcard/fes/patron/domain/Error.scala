package in.rcard.fes.patron.domain

import Domain.PatronId

enum Error {
  case AlreadyRegistered(id: PatronId)
  case UnexpectedError(message: String)
}

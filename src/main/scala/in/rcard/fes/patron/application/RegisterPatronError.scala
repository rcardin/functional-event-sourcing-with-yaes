package in.rcard.fes.patron.application

import in.rcard.fes.patron.domain.Domain.PatronId

enum RegisterPatronError {
  case AlreadyRegistered(id: PatronId)
  case UnexpectedError(message: String)
}

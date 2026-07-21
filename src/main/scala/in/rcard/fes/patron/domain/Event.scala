package in.rcard.fes.patron.domain

import Domain.{BorrowLimit, PatronId, PatronName}

private[patron] enum Event {
  case Registered(id: PatronId, name: PatronName, borrowLimit: BorrowLimit)
  case Suspended(id: PatronId)
}

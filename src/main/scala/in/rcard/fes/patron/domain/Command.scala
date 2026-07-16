package in.rcard.fes.patron.domain

import Domain.{BorrowLimit, PatronId, PatronName}

private[patron] enum Command {
  case Register(id: PatronId, name: PatronName, borrowLimit: BorrowLimit)
}

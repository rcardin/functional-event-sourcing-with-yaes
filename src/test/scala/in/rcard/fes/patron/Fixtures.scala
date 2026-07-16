package in.rcard.fes.patron

import in.rcard.fes.patron.domain.Domain

object Fixtures {
  private[patron] val CARD_ID_VALUE = "card1"
  private[patron] val CARD_ID       = Domain.PatronId(CARD_ID_VALUE)

  private[patron] val NOT_REGISTERED_CARD_ID_VALUE = "not-registered-card"
  private[patron] val NOT_REGISTERED_CARD_ID       = Domain.PatronId(NOT_REGISTERED_CARD_ID_VALUE)

  private[patron] val ALREADY_REGISTERED_CARD_ID_VALUE = "already-registered-card"
  private[patron] val ALREADY_REGISTERED_CARD_ID       = Domain.PatronId(ALREADY_REGISTERED_CARD_ID_VALUE)

  private[patron] val UNEXPECTED_CARD_ID_VALUE = "unexpected-card"
  private[patron] val UNEXPECTED_CARD_ID       = Domain.PatronId(UNEXPECTED_CARD_ID_VALUE)

  private[patron] val PATRON_NAME_VALUE = "Ada Lovelace"
  private[patron] val PATRON_NAME       = Domain.PatronName(PATRON_NAME_VALUE)

  private[patron] val BORROW_LIMIT_VALUE = 5
  private[patron] val BORROW_LIMIT       = Domain.BorrowLimit(BORROW_LIMIT_VALUE)
}

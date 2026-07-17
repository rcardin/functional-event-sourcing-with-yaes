package in.rcard.fes.patron.domain

object Domain {

  type PatronState = Seq[Event]
  object PatronState {
    val empty: PatronState = Seq.empty
  }

  enum Status {
    case NotRegistered, Active
  }

  extension (patronState: PatronState) {
    def currentStatus(id: PatronId): Status =
      patronState.reverseIterator
        .collectFirst { case Event.Registered(`id`, _, _) =>
          Status.Active
        }
        .getOrElse(Status.NotRegistered)

    def isRegistered(id: PatronId): Boolean = patronState.currentStatus(id) != Status.NotRegistered
  }

  opaque type PatronId = String
  object PatronId {
    def apply(id: String): PatronId                   = id
    extension (patronId: PatronId) def value: String = patronId
  }

  opaque type PatronName = String
  object PatronName {
    def apply(name: String): PatronName           = name
    extension (name: PatronName) def value: String = name
  }

  opaque type BorrowLimit = Int
  object BorrowLimit {
    def apply(limit: Int): BorrowLimit           = limit
    extension (limit: BorrowLimit) def value: Int = limit
  }
}

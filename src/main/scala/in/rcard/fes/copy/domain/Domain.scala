package in.rcard.fes.copy.domain

object Domain {

  type CopyState = Seq[Event]
  object CopyState {
    val empty: CopyState = Seq.empty
  }

  enum Status {
    case NotRegistered, Available, Lost, Damaged
  }

  extension (copyState: CopyState) {
    def currentStatus(id: CopyId): Status =
      copyState.foldLeft(Status.NotRegistered) { (status, event) =>
        event match {
          case Event.Registered(_id, _, _, _) if _id == id => Status.Available
          case Event.MarkedAsLost(_id) if _id == id        => Status.Lost
          case Event.MarkedAsDamaged(_id) if _id == id     => Status.Damaged
          case _                                           => status
        }
      }

    def isRegistered(id: CopyId): Boolean = copyState.currentStatus(id) != Status.NotRegistered

    def isLost(id: CopyId): Boolean = copyState.currentStatus(id) == Status.Lost

    def isDamaged(id: CopyId): Boolean = copyState.currentStatus(id) == Status.Damaged
  }

  // FIXME Insert the validations?
  opaque type CopyId = String
  object CopyId {
    def apply(id: String): CopyId                = id
    extension (copyId: CopyId) def value: String = copyId
  }

  opaque type ISBN = String
  object ISBN {
    def apply(isbn: String): ISBN            = isbn
    extension (isbn: ISBN) def value: String = isbn
  }

  opaque type Title = String
  object Title {
    def apply(title: String): Title            = title
    extension (title: Title) def value: String = title
  }

  opaque type Author = String
  object Author {
    def apply(author: String): Author            = author
    extension (author: Author) def value: String = author
  }
}

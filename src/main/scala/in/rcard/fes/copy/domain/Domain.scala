package in.rcard.fes.copy.domain

object Domain {

  type CopyState = Seq[Event]
  object CopyState {
    val empty: CopyState = Seq.empty
  }

  extension (copyState: CopyState) {
    def isRegistered(id: CopyId): Boolean = copyState.exists {
      case Event.Registered(_id, _, _, _) => _id == id
      case _                              => false
    }

    def isLost(id: CopyId): Boolean = copyState.exists {
      case Event.MarkedAsLost(_id) => _id == id
      case _                       => false
    }

    def isDamaged(id: CopyId): Boolean = copyState.exists {
      case Event.MarkedAsDamaged(_id) => _id == id
      case _                          => false
    }
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

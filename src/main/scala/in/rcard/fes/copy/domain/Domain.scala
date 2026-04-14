package in.rcard.fes.copy.domain

object Domain {

  type CopyState = Seq[Event]
  object CopyState {
    val empty: CopyState = Seq.empty
  }

  extension (copyState: CopyState) {
    def isRegistered(id: CopyId): Boolean = copyState.exists { case Event.Registered(id, _, _, _) =>
      true
    }
  }

  // FIXME Insert the validations?
  opaque type CopyId = String
  object CopyId {
    def apply(id: String): CopyId = id
  }

  opaque type ISBN = String
  object ISBN {
    def apply(isbn: String): ISBN = isbn
  }

  opaque type Title = String
  object Title {
    def apply(title: String): Title = title
  }
  
  opaque type Author = String
  object Author {
    def apply(author: String): Author = author
  }
}

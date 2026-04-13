package in.rcard.fes.copy

object Domain {
  
  type CopyState = Seq[Event]
  
  // FIXME Insert the validations?
  opaque type CopyId = String
  opaque type ISBN = String
  opaque type Title = String
  opaque type Author = String
}

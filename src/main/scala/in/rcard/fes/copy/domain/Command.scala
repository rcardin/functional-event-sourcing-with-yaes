package in.rcard.fes.copy.domain

import Domain.{Author, CopyId, ISBN, Title}

private[copy] enum Command {
  case Register(id: CopyId, isbn: ISBN, title: Title, authors: Seq[Author])
  case MarkAsLost(id: CopyId)
  case MarkAsDamaged(id: CopyId)
  case Repair(id: CopyId)
  case Remove(id: CopyId)
}

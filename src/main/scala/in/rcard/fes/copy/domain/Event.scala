package in.rcard.fes.copy.domain

import Domain.{Author, CopyId, ISBN, Title}

private [copy] enum Event {
  case Registered(id: CopyId, isbn: ISBN, title: Title, authors: Seq[Author])
}
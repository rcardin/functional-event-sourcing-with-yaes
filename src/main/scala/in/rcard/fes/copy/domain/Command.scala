package in.rcard.fes.copy.domain

import Domain.{Author, CopyId, ISBN, Title}

private[copy] enum Command {
  case Register(id: CopyId, isbn: ISBN, title: Title, author: Author)
}

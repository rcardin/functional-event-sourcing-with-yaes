package in.rcard.fes.copy

import in.rcard.fes.copy.Domain.{Author, CopyId, ISBN, Title}

private[copy] enum Command {
  case Register(id: CopyId, isbn: ISBN, title: Title, author: Author)
}

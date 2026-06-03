package in.rcard.fes.copy.domain

import Domain.{Author, CopyId, ISBN, Title}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

private[copy] enum Event {
  case Registered(id: CopyId, isbn: ISBN, title: Title, authors: Seq[Author])
}
package in.rcard.fes.copy

import in.rcard.fes.copy.domain.Domain
import in.rcard.fes.copy.domain.Domain.CopyId

object Fixtures {
  private[copy] val COPY_ID = CopyId("copy1")

  private[copy] val FOUNDATION_ISBN_VALUE   = "978-3-954-76392-4"
  private[copy] val FOUNDATION_TITLE_VALUE  = "Foundation"
  private[copy] val FOUNDATION_AUTHOR_VALUE = "Isaac Asimov"

  private[copy] val FOUNDATION_ISBN   = Domain.ISBN(FOUNDATION_ISBN_VALUE)
  private[copy] val FOUNDATION_TITLE  = Domain.Title(FOUNDATION_TITLE_VALUE)
  private[copy] val FOUNDATION_AUTHOR = Domain.Author(FOUNDATION_AUTHOR_VALUE)

  private[copy] val ALREADY_REGISTERED_ISBN_VALUE = "978-1-234-56789-7"
  private[copy] val ALREADY_REGISTERED_ISBN       = Domain.ISBN(ALREADY_REGISTERED_ISBN_VALUE)

  private[copy] val UNEXPECTED_ISBN_VALUE = "978-0-99-702549-1"
  private[copy] val UNEXPECTED_ISBN       = Domain.ISBN(UNEXPECTED_ISBN_VALUE)
}

package in.rcard.fes.copy

import in.rcard.fes.copy.domain.Domain
import in.rcard.fes.copy.domain.Domain.CopyId

object Fixtures {
  private[copy] val COPY_ID_VALUE = "copy1"
  private[copy] val COPY_ID        = CopyId(COPY_ID_VALUE)

  private[copy] val NOT_REGISTERED_COPY_ID_VALUE = "not-registered-copy"
  private[copy] val NOT_REGISTERED_COPY_ID        = CopyId(NOT_REGISTERED_COPY_ID_VALUE)

  private[copy] val ALREADY_LOST_COPY_ID_VALUE = "already-lost-copy"
  private[copy] val ALREADY_LOST_COPY_ID        = CopyId(ALREADY_LOST_COPY_ID_VALUE)

  private[copy] val ALREADY_DAMAGED_COPY_ID_VALUE = "already-damaged-copy"
  private[copy] val ALREADY_DAMAGED_COPY_ID        = CopyId(ALREADY_DAMAGED_COPY_ID_VALUE)

  private[copy] val NOT_DAMAGED_COPY_ID_VALUE = "not-damaged-copy"
  private[copy] val NOT_DAMAGED_COPY_ID        = CopyId(NOT_DAMAGED_COPY_ID_VALUE)

  private[copy] val ALREADY_REMOVED_COPY_ID_VALUE = "already-removed-copy"
  private[copy] val ALREADY_REMOVED_COPY_ID       = CopyId(ALREADY_REMOVED_COPY_ID_VALUE)

  private[copy] val UNEXPECTED_COPY_ID_VALUE = "unexpected-copy"
  private[copy] val UNEXPECTED_COPY_ID        = CopyId(UNEXPECTED_COPY_ID_VALUE)

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

  private[copy] val NOT_IN_CATALOG_ISBN_VALUE = "978-3-16-148410-0"
  private[copy] val NOT_IN_CATALOG_ISBN       = Domain.ISBN(NOT_IN_CATALOG_ISBN_VALUE)

  private[copy] val CATALOG_ERROR_ISBN_VALUE = "978-0-545-01022-1"
  private[copy] val CATALOG_ERROR_ISBN       = Domain.ISBN(CATALOG_ERROR_ISBN_VALUE)
}
